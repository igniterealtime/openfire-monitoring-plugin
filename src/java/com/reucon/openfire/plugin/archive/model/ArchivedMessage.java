package com.reucon.openfire.plugin.archive.model;

import org.jivesoftware.database.JiveID;
import org.xmpp.packet.JID;

import java.util.Date;

/**
 * An archived message.
 */
@JiveID(601)
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

    private final Long id;
    private final Date time;
    private final Direction direction;
    private final String body;
    private final JID with;
    private final String stanza;
    private final String stableId;

    public ArchivedMessage( Long id, Date time, Direction direction, JID with, String stableId, String body, String stanza) {
        this.id = id;
        this.time = time;
        this.direction = direction;
        this.with = with;
        this.stableId = stableId;
        this.body = body;
        this.stanza = stanza;
    }

    public Long getId() {
        return id;
    }

    public Date getTime() {
        return time;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getBody() {
        return body;
    }

    public String getStanza() {
        return stanza;
    }

    public JID getWith() {
        return with;
    }

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
