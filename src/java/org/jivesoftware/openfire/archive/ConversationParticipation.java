/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.archive;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.Objects;

/**
 * Participation of a user, connected from a specific resource, in a conversation. If
 * a user joins and leaves the conversation many times then we will have many instances
 * of this class.
 *
 * @author Gaston Dombiak
 */
@XmlRootElement
public class ConversationParticipation {

    @XmlElement
    private Date joined = new Date();

    @XmlElement
    private Date left;

    @XmlElement
    private String nickname;

    public ConversationParticipation() {
    }

    public ConversationParticipation(Date joined) {
        this.joined = joined;
    }

    public ConversationParticipation(Date joined, String nickname) {
        this.joined = joined;
        this.nickname = nickname;
    }

    public void participationEnded(Date left) {
        this.left = left;
    }
    
    /**
     * Returns the date when the user joined the conversation.
     *
     * @return the date when the user joined the conversation.
     */
    public Date getJoined() {
        return joined;
    }

    /**
     * Returns the date when the user left the conversation.
     *
     * @return the date when the user left the conversation.
     */
    public Date getLeft() {
        return left;
    }

    /**
     * Returns the nickname of the user used in the group conversation or
     * <tt>null</tt> if participation is in a one-to-one chat.
     *
     * @return the nickname of the user used in the group conversation.
     */
    public String getNickname() {
        return nickname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationParticipation that = (ConversationParticipation) o;
        return Objects.equals(joined, that.joined) && Objects.equals(left, that.left) && Objects.equals(nickname, that.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(joined, left, nickname);
    }
}
