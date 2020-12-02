package com.reucon.openfire.plugin.archive.model;

import org.jivesoftware.database.JiveID;
import org.xmpp.packet.JID;

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
    public enum Direction {
        /**
         * A message sent by the owner.
         */
        to,

        /**
         * A message received by the owner.
         */
        from
    }

    @Nullable
    private final Long id;

    @Nonnull
    private final Date time;

    @Nonnull
    private final Direction direction;

    @Nullable
    private final String body;

    @Nullable
    private final JID with;

    @Nullable
    private final String stanza;

    @Nullable
    private final String stableId;

    public ArchivedMessage(@Nullable final Long id, @Nonnull final Date time, @Nonnull final Direction direction, @Nullable final JID with, @Nullable final String stableId, @Nullable final String body, @Nullable final String stanza) {
        this.id = id;
        this.time = time;
        this.direction = direction;
        this.with = with;
        this.stableId = stableId;
        this.body = body;
        this.stanza = stanza;
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
    public String getStanza() {
        return stanza;
    }

    /**
     * The message peer (the 'other side' of the conversation), in respect to the owner of the archive that this message
     * is part of.
     *
     * This value will only have meaning when the original message was sent in a one-to-one conversation. When the
     * archived message was originally shared in a group chat, this value can be null.
     *
     * @return The conversation peer.
     */
    @Nullable
    public JID getWith() {
        return with;
    }

    /**
     * The first stable and unique stanza-id value in the stanza, if the stanza contains such a value.
     *
     * @return a stable and unique stanza-id value.
     */
    @Nullable
    public String getStableId()
    {
        return stableId;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("ArchivedMessage[id=").append(id).append(",");
        sb.append("stableId=").append(stableId).append(",");
        sb.append("time=").append(time).append(",");
        sb.append("direction=").append(direction).append("]");

        return sb.toString();
    }
}
