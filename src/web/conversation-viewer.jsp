<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.jivesoftware.util.*"%><%@ page import="java.util.Collection"%>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.jivesoftware.openfire.archive.*" %>
<%!
     Map<String, String> colorMap = new HashMap<>();
%>
<%
    Logger logger = LoggerFactory.getLogger("conversation-viewer-jsp");

    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME).get();

    ConversationManager conversationManager = plugin.getConversationManager();

    long conversationID = ParamUtils.getLongParameter(request, "conversationID", -1);

    Conversation conversation = null;
    if (conversationID > -1) {
        try {
            conversation = ConversationDAO.loadConversation(conversationID);
        }
        catch (NotFoundException nfe) {
            logger.error("Can't reconstruct conversation", nfe);
        }
    }

    Map<String, String> colorMap = new HashMap<>();

    if (conversation != null) {
        Collection<JID> set = conversation.getParticipants();
        int count = 0;
        for (JID jid : set) {
            if (count == 0) {
                colorMap.put(jid.toBareJID(), "blue");
            }
            else {
                colorMap.put(jid.toBareJID(), "red");
            }
            count++;
        }
    }

%>

<html>
<head>
    <meta name="decorator" content="none"/>

    <title>Conversation Viewer</title>

    <style type="text/css">
        @import "style/style.css";
    </style>
</head>

<body>

<%
    if (conversation != null) {
%>

<table width="100%">
    <% for (ArchivedMessage message : conversation.getMessages(conversationManager)) { %>
    <tr valign="top">
        <td width="1%" nowrap class="jive-description" style="color:<%= getColor(message.getFromJID()) %>">
            [<%= JiveGlobals.formatTime(message.getSentDate())%>] <%= message.getFromJID().getNode()%><%= message.getIsPMforNickname() == null ? "" : " -> " + message.getIsPMforNickname()%>:</td>
        <td><span class="jive-description"><%= StringUtils.escapeHTMLTags(message.getBody())%></span></td>
    </tr>
    <%}%>

</table>

<% }
else { %>
No conversation could be found.
<% } %>


<%!
    String getColor(JID jid){
        String color = colorMap.get(jid.toBareJID());
        if(color == null){
            LoggerFactory.getLogger("conversation-viewer-jsp").debug("Unable to find "+jid.toBareJID()+" using "+colorMap);
        }
        return color;
    }
%>
</body>
</html>
