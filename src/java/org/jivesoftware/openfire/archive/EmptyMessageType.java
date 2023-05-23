package org.jivesoftware.openfire.archive;

import org.dom4j.Element;
import org.jivesoftware.util.SAXReaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Enumeration of types of messages that typically have no body.
 */
public enum EmptyMessageType
{
    /**
     * A generic type that signifies that the type of the message child element is recognized, but must never be
     * saved in a message archive. For example: Pub/Sub event messages.
     */
    IGNORE(-1),

    /**
     * A generic type that signifies that the type of the message child element is not recognized. This differs from
     * {@link #IGNORE} as through configuration, unknown can be stored in a message archive.
     */
    UNKNOWN(1),

    /**
     * 'Markable', as specified in XEP-0333: Chat Markers
     *
     * @see <a href="https://xmpp.org/extensions/xep-0333.html">XEP-0333: Chat Markers</a>
     */
    CHATMARKER_MARKABLE(2),  //XMLNS = urn:xmpp:chat-markers:0

    /**
     * 'Received', as specified in XEP-0333: Chat Markers
     *
     * @see <a href="https://xmpp.org/extensions/xep-0333.html">XEP-0333: Chat Markers</a>
     */
    CHATMARKER_RECEIVED(4),

    /**
     * 'Displayed', as specified in XEP-0333: Chat Markers
     *
     * @see <a href="https://xmpp.org/extensions/xep-0333.html">XEP-0333: Chat Markers</a>
     */
    CHATMARKER_DISPLAYED(8),

    /**
     * 'Acknowledged', as specified in XEP-0333: Chat Markers
     *
     * @see <a href="https://xmpp.org/extensions/xep-0333.html">XEP-0333: Chat Markers</a>
     */
    CHATMARKER_ACKNOWLEDGED(16),

    /**
     * 'Retract', as specified in XEP-0424: Message Retraction
     *
     * @see <a href="https://xmpp.org/extensions/xep-0424.html">XEP-0424: Message Retraction</a>
     */
    MESSAGE_RETRACTION(32), //XMLNS = urn:xmpp:message-retract:0

    /**
     * 'Active' as specified in XEP-0085: Chat State Notifications
     *
     * @see <a href="https://xmpp.org/extensions/xep-0085.html">XEP-0085: Chat State Notifications</a>
     */
    CHATSTATE_NOTIFICATION_ACTIVE(64),  //XMLNS = http://jabber.org/protocol/chatstates'

    /**
     * 'Composing' as specified in XEP-0085: Chat State Notifications
     *
     * @see <a href="https://xmpp.org/extensions/xep-0085.html">XEP-0085: Chat State Notifications</a>
     */
    CHATSTATE_NOTIFICATION_COMPOSING(128),

    /**
     * 'Paused' as specified in XEP-0085: Chat State Notifications
     *
     * @see <a href="https://xmpp.org/extensions/xep-0085.html">XEP-0085: Chat State Notifications</a>
     */
    CHATSTATE_NOTIFICATION_PAUSED(256),

    /**
     * 'Inactive' as specified in XEP-0085: Chat State Notifications
     *
     * @see <a href="https://xmpp.org/extensions/xep-0085.html">XEP-0085: Chat State Notifications</a>
     */
    CHATSTATE_NOTIFICATION_INACTIVE(512),

    /**
     * 'Gone' as specified in XEP-0085: Chat State Notifications
     *
     * @see <a href="https://xmpp.org/extensions/xep-0085.html">XEP-0085: Chat State Notifications</a>
     */
    CHATSTATE_NOTIFICATION_GONE(1024),

    /**
     * 'Gone' as specified in XEP-0184: Message Delivery Receipts
     *
     * @see <a href="https://xmpp.org/extensions/xep-0184.html">XEP-0184: Message Delivery Receipts</a>
     */
    MESSAGE_DELIVERY_RECEIPTS_RECEIVED(2048), //XMLNS = urn:xmpp:receipts

    /**
     * 'Gone' as specified in XEP-0184: Message Delivery Receipts
     *
     * @see <a href="https://xmpp.org/extensions/xep-0184.html">XEP-0184: Message Delivery Receipts</a>
     */
    MESSAGE_DELIVERY_RECEIPTS_REQUEST(4096);

    private static final Logger Log = LoggerFactory.getLogger(EmptyMessageType.class);

    /**
     * A unique value, used for bit-masking.
     */
    private final long value;

    EmptyMessageType(final long newValue)
    {
        value = newValue;
    }

    private static Element parseWithSAX(String xmlString) throws ExecutionException, InterruptedException
    {
        return SAXReaderUtil.readRootElement(xmlString);
    }

    public static EmptyMessageType getMessageType(String stanza)
    {
        try {
            return getMessageType(parseWithSAX(stanza));
        }
        catch (Exception e) {
            Log.error("Error while parsing a message string: {}",stanza,e);
            return UNKNOWN;
        }
    }

    public static EmptyMessageType getMessageType(Element stanza)
    {
        if (stanza.selectSingleNode("//*[local-name()='event' and namespace-uri()='http://jabber.org/protocol/pubsub#event']")!=null)
        {
            return IGNORE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='markable' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return CHATMARKER_MARKABLE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='received' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return CHATMARKER_RECEIVED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='displayed' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return CHATMARKER_DISPLAYED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='acknowleged' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return CHATMARKER_ACKNOWLEDGED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='retract' and namespace-uri()='urn:xmpp:message-retract:0']")!=null)
        {
            return MESSAGE_RETRACTION;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='active' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return CHATSTATE_NOTIFICATION_ACTIVE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='composing' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return CHATSTATE_NOTIFICATION_COMPOSING;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='paused' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return CHATSTATE_NOTIFICATION_PAUSED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='inactive' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return CHATSTATE_NOTIFICATION_INACTIVE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='gone' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return CHATSTATE_NOTIFICATION_GONE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='received' and namespace-uri()='urn:xmpp:receipts']")!=null)
        {
            return MESSAGE_DELIVERY_RECEIPTS_RECEIVED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='request' and namespace-uri()='urn:xmpp:receipts']")!=null)
        {
            return MESSAGE_DELIVERY_RECEIPTS_REQUEST;
        }

        return UNKNOWN;
    }

    /**
     * Returns the unique bit-masking value for this type.
     *
     * @return a unique value.
     */
    public long getValue()
    {
        return value;
    }
}
