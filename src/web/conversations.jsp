<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="java.util.*,
                 org.jivesoftware.openfire.XMPPServer"
%>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin"%>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager"%>
<%@ page import="org.jivesoftware.openfire.archive.Conversation"%>
<%@ page import="org.jivesoftware.util.JiveGlobals"%>
<%@ page import="org.xmpp.packet.JID"%>
<%@ page import="org.jivesoftware.openfire.user.UserManager"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.openfire.archive.MonitoringConstants" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME).get();
    ConversationManager conversationManager = plugin.getConversationManager();

    XMPPServer server = XMPPServer.getInstance();
    UserManager userManager = UserManager.getInstance();
%>

<html>
    <head>
        <title>Conversations</title>
        <meta name="pageID" content="active-conversations"/>
    </head>
    <body>

<style type="text/css">
    @import "style/style.css";

    @keyframes FadeIn {
        from {
            background-color: lightyellow;
        }

        to {
            background-color: unset;
        }
    }

    #conversations tr {
        animation: FadeIn 3.0s ease-in-out forwards;
    }
</style>
<script type="text/javascript">
function conversationUpdater() {
    let xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                updateConversations(JSON.parse(xhr.responseText));
            }
            setTimeout(conversationUpdater, 10000);
        }
    }
    xhr.open("GET", '/plugins/monitoring/api/conversations', true);
    xhr.send(null);
}

setTimeout(conversationUpdater, 10000);

function updateConversations(data) {
    let users;
    let conversation;
    let i;
    let conversationsTable = document.getElementById('conversations');
    let rows = conversationsTable.getElementsByTagName("tr");
    // loop over existing rows in the table
    let rowsToDelete = [];
    for (i = 0; i < rows.length; i++) {
        // is this a conversation row?
        if (rows[i].id === 'noconversations') {
            rowsToDelete.push(i);
        } else if (rows[i].id !== '') {
            // does the conversation exist in update we received?
            convID = rows[i].id.replace('conversation-', '');
            if (data[convID] !== undefined) {

                row = rows[i];
                cells = row.getElementsByTagName('td');
                conversation = data[convID];
                if (cells[3].innerHTML !== conversation.messageCount) {
                    if (!conversation.allParticipants) {
                        users = conversation.participant1 + '<br />' + conversation.participant2;
                        cells[0].innerHTML = users;
                    }
                    cells[1].innerHTML = conversation.duration;
                    cells[2].innerHTML = conversation.lastActivity;
                    cells[3].innerHTML = conversation.messageCount;
                }
            // doesn't exist in update, delete from table
            } else {
                rowsToDelete.push(i);
            }
        }
    }

    for (i=0; i<rowsToDelete.length; i++) {
        conversationsTable.deleteRow(rowsToDelete[i]);
    }

    // then add any new conversations from the update
    let counter = 0;
    for (let c in data) {
        counter++;
        // does this conversation already exist?
        if (!document.getElementById('conversation-' + c)) {
            conversation = data[c];
            if (!conversation.allParticipants) {
                users = conversation.participant1 + '<br />' + conversation.participant2;
            } else {
                users = '<fmt:message key="archive.group_conversation"/>';
                users = users.replace('{0}', '<a href="../../muc-room-occupants.jsp?roomJID=' + conversation.roomJID + '">');
                users = users.replace('{1}', '</a');
            }
            let newTR = document.createElement("tr");
            newTR.setAttribute('id', 'conversation-' + c)
            conversationsTable.appendChild(newTR);
            let TD = document.createElement("TD");
            TD.innerHTML = users;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.duration;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.lastActivity;
            newTR.appendChild(TD);

            TD = document.createElement("TD");
            TD.innerHTML = conversation.messageCount;
            newTR.appendChild(TD);
        }
    }

    // update activeConversations number
    document.getElementById('activeConversations').innerHTML = counter;

    // When there's no data in the table, add the 'no conversations' placeholder.
    if (counter === 0 && !document.getElementById('noconversations')) {
        let noConvTR = document.createElement("tr");
        noConvTR.setAttribute('id', 'noconversations');
        conversationsTable.appendChild(noConvTR);
        let noConvTD = document.createElement("TD")
        noConvTD.setAttribute('colspan', '4');
        noConvTD.innerHTML = '<fmt:message key="archive.converations.no_conversations" />';
        noConvTR.appendChild(noConvTD);
    }
}

//# sourceURL=conversations.jsp
</script>

<% if (!ClusterManager.findRemotePluginsWithDifferentVersion(MonitoringConstants.PLUGIN_NAME).isEmpty()) { %>
<div class="warning">
    <fmt:message key="warning.clustering.versions">
        <fmt:param value="<a href='/system-clustering.jsp'>" />
        <fmt:param value="</a>" />
    </fmt:message>
</div>
<br/>
<% } %>

<!-- <a href="#" onclick="conversationUpdater(); return false;">click me</a> -->
<p>
    <fmt:message key="archive.conversations" />
    <span id="activeConversations"><%= conversationManager.getConversationCount() %></span>
</p>

<%
    Collection<Conversation> conversations = conversationManager.getConversations();
%>


<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%" id="conversations">
<thead>
    <tr>
        <th nowrap><fmt:message key="archive.conversations.users" /></th>
        <th nowrap><fmt:message key="archive.conversations.duration" /></th>
        <th nowrap><fmt:message key="archive.conversations.lastactivity" /></th>
        <th nowrap><fmt:message key="archive.conversations.messages" /></th>
    </tr>
</thead>
<tbody>
    <%
        if (conversations.isEmpty()) {
    %>
        <tr id="noconversations">
            <td colspan="4">
                <fmt:message key="archive.converations.no_conversations" />
            </td>
        </tr>

    <%  } %>
    <%
        for (Conversation conversation : conversations) {
            Collection<JID> participants = conversation.getParticipants();
    %>
    <tr id="conversation-<%= conversation.getConversationID()%>">
        <td>
            <% if (conversation.getRoom() == null) { %>
                <% for (JID jid : participants) { %>
                    <% if (server.isLocal(jid) && userManager.isRegisteredUser(jid, false)) { %>
                        <a title='User Link' href="/user-properties.jsp?username=<%= jid.getNode() %>"><%= StringUtils.escapeHTMLTags(jid.toBareJID()) %></a><br />
                    <% } else { %>
                        <%= StringUtils.escapeHTMLTags(jid.toBareJID()) %><br/>
                    <% } %>
                <% } %>
            <% } else {
                pageContext.setAttribute( "roomBareJID", URLEncoder.encode(conversation.getRoom().toBareJID(), "UTF-8") ); %>
                <fmt:message key="archive.group_conversation">
                    <fmt:param value="<a href=\"../../muc-room-occupants.jsp?roomJID=${roomBareJID}\">" />
                    <fmt:param value="</a>" />
                </fmt:message>
            <% } %>
        </td>
        <%
            long duration = conversation.getLastActivity().getTime() -
                    conversation.getStartDate().getTime();
        %>
        <td><%= StringUtils.getTimeFromLong(duration) %></td>
        <td><%= JiveGlobals.formatTime(conversation.getLastActivity()) %></td>
        <td><%= conversation.getMessageCount() %></td>
    </tr>
    <%  } %>
</tbody>
</table>
</div>

</body>
</html>
