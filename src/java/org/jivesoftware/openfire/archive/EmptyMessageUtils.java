package org.jivesoftware.openfire.archive;

import java.io.ByteArrayInputStream;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmptyMessageUtils {

    private static final Logger Log = LoggerFactory.getLogger(EmptyMessageUtils.class);

    public enum EmptyMessageType {
        TYPE_EVENT(-1),
        TYPE_UNKNOWN(1),

        TYPE_CHATMARKER_MARKABLE(2),  //XMLNS = urn:xmpp:chat-markers:0
        TYPE_CHATMARKER_RECEIVED(4),
        TYPE_CHATMARKER_DISPLAYED(8),
        TYPE_CHATMARKER_ACKNOWLEDGED(16),  

        TYPE_MESSAGE_RETRACTION(32), //XMLNS = urn:xmpp:message-retract:0

        TYPE_CHATSTATE_NOTIFICATION_ACTIVE(64),  //XMLNS = http://jabber.org/protocol/chatstates'
        TYPE_CHATSTATE_NOTIFICATION_COMPOSING(128),
        TYPE_CHATSTATE_NOTIFICATION_PAUSED(256),
        TYPE_CHATSTATE_NOTIFICATION_INACTIVE(512),
        TYPE_CHATSTATE_NOTIFICATION_GONE(1024),

        TYPE_MESSAGE_DELIVERY_RECEIPTS_RECEIVED(2048), //XMLNS = urn:xmpp:receipts
        TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST(4096);

        private final long value;

        EmptyMessageType(final long newValue) {
            value = newValue;
        }

        public long getValue() { return value; }
    }

    private static Element parseWithSAX(String xmlString) throws DocumentException {
        SAXReader xmlReader = new SAXReader();
        return xmlReader.read(new ByteArrayInputStream(xmlString.getBytes())).getRootElement();
    }

    public static EmptyMessageType getMessageType(String stanza)
    {
        try {
            return getMessageType(parseWithSAX(stanza));
        }
        catch (Exception e) {
            Log.error("Error while parsing a message string: {}",stanza,e);
            return EmptyMessageType.TYPE_UNKNOWN;
        }
    }

    public static EmptyMessageType getMessageType(Element stanza)
    {
        if (stanza.selectSingleNode("//*[local-name()='event' and namespace-uri()='http://jabber.org/protocol/pubsub#event']")!=null)
        {
            return EmptyMessageType.TYPE_EVENT;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='markable' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return EmptyMessageType.TYPE_CHATMARKER_MARKABLE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='received' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return EmptyMessageType.TYPE_CHATMARKER_RECEIVED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='displayed' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return EmptyMessageType.TYPE_CHATMARKER_DISPLAYED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='acknowleged' and namespace-uri()='urn:xmpp:chat-markers:0']")!=null)
        {
            return EmptyMessageType.TYPE_CHATMARKER_ACKNOWLEDGED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='retract' and namespace-uri()='urn:xmpp:message-retract:0']")!=null)
        {
            return EmptyMessageType.TYPE_MESSAGE_RETRACTION;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='active' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_ACTIVE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='composing' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_COMPOSING;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='paused' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_PAUSED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='inactive' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_INACTIVE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='gone' and namespace-uri()='http://jabber.org/protocol/chatstates']")!=null)
        {
            return EmptyMessageType.TYPE_CHATSTATE_NOTIFICATION_GONE;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='received' and namespace-uri()='urn:xmpp:receipts']")!=null)
        {
            return EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_RECEIVED;
        }
        else
        if (stanza.selectSingleNode("//*[local-name()='request' and namespace-uri()='urn:xmpp:receipts']")!=null)
        {
            return EmptyMessageType.TYPE_MESSAGE_DELIVERY_RECEIPTS_REQUEST;
        }

        return EmptyMessageType.TYPE_UNKNOWN;
    }
}