package com.reucon.openfire.plugin.archive.model;

import org.dom4j.*;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Date;

/**
 * An archived message.
 */
@JiveID(601)
@Immutable
public class ArchivedMessage {

    public static final Logger Log = LoggerFactory.getLogger( ArchivedMessage.class );

    private static SystemProperty<Boolean> OF1804_DISABLE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("conversation.OF-1804.disable")
        .setDefaultValue(false)
        .setDynamic(true)
        .setPlugin(MonitoringConstants.PLUGIN_NAME)
        .build();

    public enum Direction {
        /**
         * A message sent by the owner.
         */
        to,

        /**
         * A message received by the owner.
         */
        from;

        /**
         * Returns a direction instance for a particular stanza, based on the owner of the archive that the stanza is
         * in, and the addressee ('to') of the stanza.
         *
         * @param owner The owner of the archive that a message is in.
         * @param addressee The addressee of the stanza.
         * @return The direction of the stanza.
         */
        public static Direction getDirection(@Nonnull final JID owner, @Nonnull final JID addressee) {
            if (owner.asBareJID().equals(addressee.asBareJID())) {
                return Direction.from;
            } else {
                return Direction.to;
            }
        }
    }

    @Nullable
    private final Long id;

    @Nonnull
    private final Date time;

    @Nonnull
    private final Direction direction;

    @Nullable
    private final String body;

    @Nonnull
    private final JID with;

    // Issue #373: Lazily populated with the value of #rawStanza
    @Nullable
    private Message stanza;

    @Nullable
    // Issue #373: Lazily populates #stanza
    private String rawStanza;

    public ArchivedMessage(@Nullable final Long id, @Nonnull final Date time, @Nonnull final Direction direction, @Nonnull final JID with, @Nullable final String body, @Nullable final String stanza) throws DocumentException {
        this.id = id;
        this.time = time;
        this.direction = direction;
        this.with = with;
        this.body = body;

        // Issue #373: Do not parse the stanza now, as this incurs quite a bit of resource usage. This constructor is
        // often called when a database resource is held. It is desirable to release that database resource as soon as possible.
        this.rawStanza = stanza;
    }

    /**
     * The database identifier used to store this message. Expected to be non-null, unless an instance has not been
     * persisted in the database yet.
     *
     * @return A database identifier
     */
    @Nullable
    public Long getId() {
        return id;
    }

    /**
     * The instant when the message was originally sent.
     *
     * @return date that the message wos originally sent.
     */
    @Nonnull
    public Date getTime() {
        return time;
    }

    /**
     * Defines if the message was originally sent, or received, by the owner of the archive that this message is part of.
     *
     * @return indication if message was originally sent or received.
     */
    @Nonnull
    public Direction getDirection() {
        return direction;
    }

    /**
     * The textual content of the message that was sent, if the message had any text.
     *
     * @return Message content
     */
    @Nullable
    public String getBody() {
        return body;
    }

    /**
     * The XML representation of the XMPP stanza that was used to transmit the message.
     *
     * Note that older version of the Monitoring plugin did not store the XMPP stanza to the database. As a result,
     * messages that were archived by those versions of the plugin do not include a stanza.
     *
     * @return XMPP packet
     */
    @Nullable
    public Message getStanza() {
        if (this.stanza != null) {
            // Return lazily-loaded stanza
            return this.stanza;
        }

        if (rawStanza == null || rawStanza.isEmpty()) {
            // There is no data to lazily load.
            return null;
        }

        synchronized (this) {
            // Issue #373: Lazily load stanza from rawStanza. This can be resource intensive. Guarded with mutex to prevent duplicate execution.
            Message stanzaResult;
            try {
                final Document doc = DocumentHelper.parseText(rawStanza);
                stanzaResult = new Message(doc.getRootElement());
            } catch (DocumentException de) {
                Log.debug("Unable to parse (non-empty) stanza (id: {})", id, de);
                stanzaResult = null;
            }
            this.stanza = stanzaResult;

            if (this.stanza != null && !OF1804_DISABLE.getValue())
            {
                // Prior to OF-1804 (Openfire 4.4.0), the stanza was logged with a formatter applied.
                // This causes message formatting to be modified (notably, new lines could be altered).
                // This workaround restores the original body text, that was stored in a different column.
                this.stanza.setBody( body );
            }

            // Prevent data duplication: Remove the now-processed raw data.
            this.rawStanza = null;
        }

        return this.stanza;
    }

    /**
     * The message peer (the 'other side' of the conversation), in respect to the owner of the archive that this message
     * is part of.
     *
     * When the archived message was originally shared in a group chat, this value will reference the in-room address
     * of the participant that sent the message (eg: room@service/nickname).
     *
     * @return The conversation peer.
     */
    @Nonnull
    public JID getWith() {
        return with;
    }

    /**
     * The first stable and unique stanza-id value in the stanza that was set by owner of the message archive, if the
     * stanza contains such a value.
     *
     * @return a stable and unique stanza-id value.
     */
    @Nullable
    public String getStableId(final JID owner)
    {
        final Message stanza = getStanza();
        if (stanza == null) {
            return null;
        }

        try {
            return StanzaIDUtil.findFirstUniqueAndStableStanzaID(stanza, owner.toBareJID());
        } catch (Exception e) {
            Log.warn("An exception occurred while parsing message with ID {}", id, e);
            return null;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("ArchivedMessage[id=").append(id).append(",");
        sb.append("time=").append(time).append(",");
        sb.append("direction=").append(direction).append("]");

        return sb.toString();
    }

    /**
     * When the archived message does not include the original stanza, this method can be used to recreate a stanza from
     * the individual parts that did get persisted.
     *
     * Note that the result of this method will not include most of the metadata that would have been sent in the original
     * stanza. When the original stanza is available, that should be preferred over the result of this method. This
     * method explicitly does not return or use a stanza that is available in the archivedMessage argument, assuming
     * that the caller has evaluated that, and choose (for whatever reason) to not use that (eg: it might be malformed?)
     *
     * When the archived message does not contain certain optional data (such as a body), this method cannot recreate a
     * message stanza. In such cases, this method returns null.
     *
     * @param archivedMessage The archived message for which to recreate a stanza.
     * @param archiveOwner The owner of the archive from which to recreate a stanza
     * @return A recreated stanza.
     */
    @Nullable
    public static Message recreateStanza( @Nonnull final ArchivedMessage archivedMessage, @Nonnull final JID archiveOwner )
    {
        if ( archivedMessage.getBody() == null || archivedMessage.getBody().isEmpty() ) {
            Log.trace("Cannot reconstruct stanza for archived message ID {}, as it has no body.", archivedMessage.getId());
            return null;
        }

        // Try creating a fake one from the body.
        JID to;
        JID from;
        if (archivedMessage.getDirection() == ArchivedMessage.Direction.to) {
            // message sent by the archive owner;
            to = archivedMessage.getWith();
            from = archiveOwner;
        } else {
            // message received by the archive owner;
            to = archiveOwner;
            from = archivedMessage.getWith();
        }

        // MUC messages only have a 'from'.
        final boolean isToMuc = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService( to ) != null;
        final boolean isFromMuc = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService( from ) != null;
        if (isToMuc) {
            from = to;
            to = null; // XEP-0313 specifies in section 5.1.2 MUC Archives: When sending out the archives to a requesting client, the forwarded stanza MUST NOT have a 'to' attribute.
        } else if (isFromMuc) {
            to = null;
        }

        final Message result = new Message();
        result.setFrom(from);
        result.setTo(to);
        result.setType( (isToMuc || isFromMuc) ? Message.Type.groupchat : Message.Type.chat );
        result.setBody(archivedMessage.getBody());

        Log.trace( "Reconstructed stanza for archived message with ID {} (only a body was stored): {}", archivedMessage.getId(), result );
        return result;
    }
}
