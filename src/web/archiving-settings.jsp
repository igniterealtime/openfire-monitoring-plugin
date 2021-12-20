<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="/error.jsp" %>
<%@ page import="org.jivesoftware.openfire.plugin.MonitoringPlugin"%>
<%@ page import="org.jivesoftware.openfire.archive.ArchiveIndexer" %>
<%@ page import="org.jivesoftware.openfire.archive.ConversationManager, org.jivesoftware.util.ByteFormat, org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.util.StringUtils" %>
<%@ page import="org.jivesoftware.util.CookieUtils" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="java.time.Duration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="com.reucon.openfire.plugin.archive.impl.MucIndexer" %>
<%@ page import="org.jivesoftware.openfire.plugin.service.LogAPI" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="org.jivesoftware.openfire.http.HttpBindManager" %>
<%@ page import="com.reucon.openfire.plugin.archive.impl.MessageIndexer" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>
<%
    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
    ConversationManager conversationManager = plugin.getConversationManager();
    ArchiveIndexer archiveIndexer = plugin.getArchiveIndexer();
    MucIndexer mucIndexer = plugin.getMucIndexer();
    MessageIndexer messageIndexer = plugin.getMessageIndexer();

    ByteFormat byteFormatter = new ByteFormat();
    String indexSize = byteFormatter.format(archiveIndexer.getIndexSize() + mucIndexer.getIndexSize() + messageIndexer.getIndexSize());
%>

<html>
<head>
<title><fmt:message key="archive.settings.title"/></title>
<meta name="pageID" content="archiving-settings"/>
<link rel="stylesheet" type="text/css" href="style/style.css">
<script type="text/javascript">
    // Calls a getBuildProgress
    function getBuildProgress() {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", '/plugins/monitoring/api/buildprogress');
        xhr.onload = function (e) {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    showBuildProgress(xhr.responseText);
                }
            }
        };
        xhr.send(null);
    }

    function showBuildProgress(progress) {
        var rebuildElement = document.getElementById("rebuildElement");
        if (progress != null && progress != -1){
            document.getElementById("rebuild").style.display="block";
            // Update progress item.
            rebuildElement.style.display = '';
            var rebuildProgress = document.getElementById('rebuildProgress');
            rebuildProgress.innerHTML = progress;
            setTimeout("getBuildProgress()", 1000);
        }
        else {
            var rebuildProgress = document.getElementById('rebuildProgress');
            rebuildProgress.innerHTML = "100";
            // Effect.Fade('rebuildElement');
            document.getElementById("rebuild").style.display="none";
        }
    }
    
    //# sourceURL=archiving-settings.jsp 
</script>
<style type="text/css">
    .small-label {
        font-size: 11px;
        font-weight: bold;
        font-family: verdana;
    }

    .small-text {
        font-size: 11px;
        font-family: verdana;
    }

    .stat {
        border: 1px;
        border-color: #ccc;
        border-style: dotted;
    }

    .conversation-body {
        color: black;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label1 {
        color: blue;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-label2 {
        color: red;
        font-size: 11px;
        font-family: verdana;
    }

    .conversation-table {
        font-family: verdana;
        font-size: 12px;
    }

    .light-gray-border {
        border-color: #bbb;
        border-style: solid;
        border-width: 1px 1px 1px 1px;
    }

    .light-gray-border-bottom {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

    .content {
        border-color: #bbb;
        border-style: solid;
        border-width: 0px 0px 1px 0px;
    }

    /* Default DOM Tooltip Style */
    div.domTT {
        border: 1px solid #bbb;
        background-color: #F9F5D5;
        font-family: arial;
        font-size: 9px;
        padding: 5px;
    }

    div.domTT .caption {
        font-family: serif;
        font-size: 12px;
        font-weight: bold;
        padding: 1px 2px;
        color: #FFFFFF;
    }

    div.domTT .contents {
        font-size: 12px;
        font-family: sans-serif;
        padding: 3px 2px;
    }

    .textfield {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
        background: #efefef;
    }

    .keyword-field {
        font-size: 11px;
        font-family: verdana;
        padding: 3px 2px;
    }


</style>

<style type="text/css">
    @import "style/style.css";
</style>
</head>

<body>

<% // Get parameters
    boolean update = request.getParameter("update") != null;
    boolean updateLogSettings = request.getParameter("updateLogSettings") != null;
    boolean messageArchiving = conversationManager.isMessageArchivingEnabled();
    boolean roomArchiving = conversationManager.isRoomArchivingEnabled();
    boolean roomArchivingStanzas = conversationManager.isRoomArchivingStanzasEnabled();
    Duration idleTime = Duration.ofMinutes(ParamUtils.getLongParameter(request, "idleTime", conversationManager.getIdleTime().toMinutes()));
    Duration maxTime = Duration.ofMinutes(ParamUtils.getLongParameter(request, "maxTime", conversationManager.getMaxTime().toMinutes()));
    
    Duration maxAge = Duration.ofDays(ParamUtils.getLongParameter(request, "maxAge", conversationManager.getMaxAge().toDays()));
    Duration maxRetrievable = Duration.ofDays(ParamUtils.getLongParameter(request, "maxRetrievable", conversationManager.getMaxRetrievable().toDays()));
    
    boolean rebuildIndex = request.getParameter("rebuild") != null;
    boolean calculateCounts = request.getParameter("calculateCounts") != null;
    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    Map errors = new HashMap();
    String errorMessage = "";

    if ((rebuildIndex || update || updateLogSettings) && (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam))) {
        rebuildIndex = false;
        update = false;
        errorMessage = "CSRF Failure.";
        errors.put("csrf", "");
    }
    csrfParam = StringUtils.randomString(16);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (request.getParameter("cancel") != null) {
        response.sendRedirect("archiving-settings.jsp");
        return;
    }

    int archivedMessageCount = -1;
    int archivedConversationCount = -1;
    if ( calculateCounts ) {
        archivedMessageCount = conversationManager.getArchivedMessageCount();
        archivedConversationCount = conversationManager.getArchivedConversationCount();
    }

    if (rebuildIndex) {
        final boolean archiveRebuildStarted = archiveIndexer.rebuildIndex() != null;
        final boolean mucRebuildStarted = mucIndexer.rebuildIndex() != null;
        final boolean messageRebuildStarted = messageIndexer.rebuildIndex() != null;
        if ( !archiveRebuildStarted || !mucRebuildStarted || !messageRebuildStarted) {
            errors.put("rebuildIndex", "");
            errorMessage = "Archive Index rebuild failed.";
        }
    }

    // Update the session kick policy if requested
    if (update) {
        // New settings for message archiving.
        boolean metadataArchiving = request.getParameter("metadataArchiving") != null;
        messageArchiving = request.getParameter("messageArchiving") != null;
        roomArchiving = request.getParameter("roomArchiving") != null;
        roomArchivingStanzas = request.getParameter("roomArchivingStanzas") != null;
        String roomsArchived = request.getParameter("roomsArchived");

        // Validate params
        if (idleTime.toMinutes() < 1) {
            errors.put("idleTime", "");
            errorMessage = "Idle Time must be greater than 0.";
        }
        if (maxTime.toMinutes() < 1) {
            errors.put("maxTime", "");
            errorMessage = "Max Time must be greater than 0.";
        }
        if (roomsArchived != null && roomsArchived.contains("@")) {
            errors.put("roomsArchived", "");
            errorMessage = "Only name of local rooms should be specified.";
        }
        if (maxAge.toDays() < 0) {
            errors.put("maxAge", "");
            errorMessage = "Max Age must be greater than or equal to 0.";
        }
        if (maxRetrievable.toDays() < 0) {
            errors.put("maxRetrievable", "");
            errorMessage = "Max Retrievable must be greater than or equal to 0.";
        }
        // If no errors, continue:
        if (errors.size() == 0) {
            conversationManager.setMetadataArchivingEnabled(metadataArchiving);
            conversationManager.setMessageArchivingEnabled(messageArchiving);
            conversationManager.setRoomArchivingEnabled(roomArchiving);
            conversationManager.setRoomArchivingStanzasEnabled(roomArchivingStanzas);
            conversationManager.setRoomsArchived(StringUtils.stringToCollection(roomsArchived));
            conversationManager.setIdleTime(idleTime);
            conversationManager.setMaxTime(maxTime);
            
            conversationManager.setMaxAge(maxAge);
            conversationManager.setMaxRetrievable(maxRetrievable);

            webManager.logEvent("Changed archive settings (monitoring plugin)",
                                "Metadata Archiving Enabled: " + metadataArchiving
                                    + ", Message Archiving Enabled: " + messageArchiving
                                    + ", Room Archiving Enabled: " + roomArchiving
                                    + ", Room Archiving Stanzas Enabled: " + roomArchivingStanzas
                                    + ", RoomsArchived: " + StringUtils.stringToCollection(roomsArchived)
                                    + ", Idle Time: " + idleTime.toMinutes()
                                    + ", Max Time: " + maxTime.toMinutes()
                                    + ", Max Age: " + maxAge.toDays()
                                    + ", Max Retrievable: " + maxRetrievable.toDays() );

%>
<div class="success">
    <fmt:message key="archive.settings.success"/>
</div><br>
<%
        }
    }

    if (updateLogSettings) {
        boolean publicLogs = request.getParameter("publicLogs") != null;

        if (errors.isEmpty()) {
            LogAPI.PROP_ENABLED.setValue(publicLogs);

            webManager.logEvent("Changed public logs settings (monitoring plugin)",
                                "Expose public room logs API: " + publicLogs);
%>
<div class="success">
    <fmt:message key="archive.settings.success"/>
</div><br>
<%
        }
    }
%>

<div class="success" id="rebuild" style="display: <%=rebuildIndex?"block":"none"%>">
    <fmt:message key="archive.settings.rebuild.success"/>
</div><br/>

<% if (errors.size() > 0) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<p>
    <fmt:message key="archive.settings.description"/>
</p>

<form action="archiving-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${csrf}">
    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
            <tr>
                <th colspan="3"><fmt:message key="archive.settings.message.metadata.title" /></th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td colspan="3"><p><fmt:message key="archive.settings.message.metadata.description" /></p></td>
            </tr>
            <tr>
                <td colspan="2" width="90%"><label class="jive-label" for="metadata"><fmt:message key="archive.settings.enable.metadata"/>:</label><br>
                <fmt:message key="archive.settings.enable.metadata.description"/></td>
                <td><input type="checkbox" id="metadata" name="metadataArchiving" <%= conversationManager.isMetadataArchivingEnabled() ? "checked" : "" %> /></td>
            </tr>
            <tr>
                <td colspan="3"><label class="jive-label"><fmt:message key="archive.settings.enable.message"/>:</label><br>
                <fmt:message key="archive.settings.enable.message.description"/><br>
                <table width=70% align=right border="0" cellpadding="3" cellspacing="0">
                    <tr>
                        <td><fmt:message key="archive.settings.one_to_one"/></td>
                        <td><input type="checkbox" name="messageArchiving" <%= conversationManager.isMessageArchivingEnabled() ? "checked" : ""%> /></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="archive.settings.group_chats"/></td>
                        <td><input type="checkbox" name="roomArchiving" <%= conversationManager.isRoomArchivingEnabled() ? "checked" : ""%> /></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="archive.settings.group_chats.stanzas"/></td>
                        <td><input type="checkbox" name="roomArchivingStanzas" <%= conversationManager.isRoomArchivingStanzasEnabled() ? "checked" : ""%> /></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="archive.settings.certain_rooms"/></td>
                        <td><textarea name="roomsArchived" cols="30" rows="2" wrap="virtual"><%=StringUtils.escapeForXML( StringUtils.collectionToString(conversationManager.getRoomsArchived()) ) %></textarea></td>
                    </tr>
                </table>
                </td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.idle.time"/>:</label><br>
                <fmt:message key="archive.settings.idle.time.description"/></td>
                <td><input type="text" name="idleTime" size="10" maxlength="10" value="<%= conversationManager.getIdleTime().toMinutes()%>" /></td>
                <td></td>
            </tr>
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.time"/>:</label><br>
                <fmt:message key="archive.settings.max.time.description"/><br><br></td>
                <td><input type="text" name="maxTime" size="10" maxlength="10" value="<%= conversationManager.getMaxTime().toMinutes()%>" /></td>
                <td></td>
            </tr>
            
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.age"/>:</label><br>
                <fmt:message key="archive.settings.max.age.description"/><br><br>
                <font color="FF0000"><fmt:message key="archive.settings.max.age.warning"/></font><br><br></td>
                <td><input type="text" name="maxAge" size="10" maxlength="10" value="<%= conversationManager.getMaxAge().toDays()%>" /></td>
                <td></td>
            </tr>
            
            <tr>
                <td><label class="jive-label"><fmt:message key="archive.settings.max.retrievable"/>:</label><br>
                <fmt:message key="archive.settings.max.retrievable.description"/><br><br></td>
                <td><input type="text" name="maxRetrievable" size="10" maxlength="10" value="<%= conversationManager.getMaxRetrievable().toDays()%>" /></td>
                <td></td>
            </tr>
            
        </tbody>
    </table>


    <input type="submit" name="update" value="<fmt:message key="archive.settings.update.settings" />">
    <input type="submit" name="cancel" value="<fmt:message key="archive.settings.cancel" />">

    <br>
    <br>
    <% if (messageArchiving || roomArchiving) { %>
    <br>

    <% if ( ! HttpBindManager.getInstance().isHttpBindEnabled() ) { %>

    <div class="jive-warning">
        <table cellpadding="0" cellspacing="0" border="0">
            <tbody>
            <tr><td class="jive-icon"><img src="images/warning-16x16.gif" width="16" height="16" border="0" alt=""></td>
                <td class="jive-icon-label">
                    <fmt:message key="warning.httpbinding.disabled">
                        <fmt:param value="<a href=\"../../http-bind.jsp\">"/>
                        <fmt:param value="</a>"/>
                    </fmt:message>
                </td></tr>
            </tbody>
        </table>
    </div><br>

    <%  } %>

    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
        <tr>
            <th colspan="2"><fmt:message key="archive.settings.logs.title" /></th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td colspan="2">
                <p><fmt:message key="archive.settings.logs.description" /></p>
                <p>
                <% if ( HttpBindManager.getInstance().isHttpBindActive() ) {
                    final String unsecuredAddress = "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HttpBindManager.getInstance().getHttpBindUnsecurePort() + "/" + MonitoringPlugin.CONTEXT_ROOT + "/";
                %>
                <fmt:message key="archive.settings.logs.link.unsecure">
                    <fmt:param value="<%=unsecuredAddress%>"/>
                </fmt:message>
                <% } %>
                <% if ( HttpBindManager.getInstance().isHttpsBindActive() ) {
                    final String securedAddress = "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HttpBindManager.getInstance().getHttpBindSecurePort() + "/" + MonitoringPlugin.CONTEXT_ROOT + "/";
                %>
                <fmt:message key="archive.settings.logs.link.secure">
                    <fmt:param value="<%=securedAddress%>"/>
                </fmt:message>
                <% } %>
                </p>
            </td>
        </tr>
        <tr>
            <td width="90%"><label class="jive-label" for="publicLogs"><fmt:message key="archive.settings.logs.public.enable"/>:</label><br>
                <fmt:message key="archive.settings.logs.public.enable.description"/></td>
            <td><input type="checkbox" id="publicLogs" name="publicLogs" <%= LogAPI.PROP_ENABLED.getValue() ? "checked" : "" %> /></td>
        </tr>
        </tbody>
    </table>

    <input type="submit" name="updateLogSettings" value="<fmt:message key="archive.settings.update.settings" />">
    <input type="submit" name="cancel" value="<fmt:message key="archive.settings.cancel" />">

    <br>
    <br>
    <br>

    <table class="settingsTable" cellpadding="3" cellspacing="0" border="0" width="90%">
        <thead>
            <tr>
               <th colspan="3" width="100%"><fmt:message key="archive.settings.index.settings"/></th>
            </tr>
        </thead>
        <tbody>
           <tr>
               <td colspan="3" width="100%"><p><fmt:message key="archive.settings.index.settings.description"/></p></td>
           </tr>
           <tr valign="top">
               <td width="80%"><b><fmt:message key="archive.settings.current.index"/></b> - <fmt:message key="archive.settings.current.index.description"/></td>
               <td><%= indexSize %></td>
               <td></td>
           </tr>
           <tr valign="top">
               <td><b><fmt:message key="archive.settings.message.count"/></b> - <fmt:message key="archive.settings.message.count.description"/></td>
               <% if ( archivedMessageCount > -1 ) { %>
               <td><%= archivedMessageCount %></td>
               <% } else { %>
               <td rowspan="2"><input type="submit" name="calculateCounts" value="<fmt:message key="archive.settings.calculateCounts" />"/></td>
               <% } %>
               <td></td>
           </tr>
           <tr valign="top">
               <td><b><fmt:message key="archive.settings.conversation.count"/></b> - <fmt:message key="archive.settings.conversation.count.description"/><br><br></td>
               <% if ( archivedConversationCount > -1 ) { %>
               <td><%= archivedConversationCount %></td>
               <% } %>
               <td></td>
           </tr>
        </tbody>
    </table>

    <input type="submit" name="rebuild" value="<fmt:message key="archive.settings.rebuild" />"/>
    <span id="rebuildElement" style="display:none;" class="jive-description">Rebuilding is <span id="rebuildProgress"></span>% complete.</span>

    <%} %>
</form>

<script type="text/javascript">
    getBuildProgress();
</script>

</body>
</html>
