<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page errorPage="/error.jsp" import="org.jivesoftware.openfire.plugin.MonitoringPlugin"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.user.UserNameManager" %>
<%@ page import="org.jivesoftware.openfire.user.UserNotFoundException" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.text.DateFormat"%>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="com.reucon.openfire.plugin.archive.xep.AbstractXepSupport" %>
<%@ page import="org.jivesoftware.openfire.archive.*" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    Logger logger = LoggerFactory.getLogger("archive-search-jsp");

    // Get handle on the Monitoring plugin
    MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME).get();
    ArchiveSearcher archiveSearcher = plugin.getArchiveSearcher();
    ConversationManager conversationManager = plugin.getConversationManager();

    boolean submit = request.getParameter("submitForm") != null;
    if (!submit) {
        submit = request.getParameter("parseRange") != null;
    }
    String query = request.getParameter("keywords");

    Collection<Conversation> conversations = null;


    String participant1 = request.getParameter("participant1");
    String participant2 = request.getParameter("participant2");

    String startDate = request.getParameter("startDate");
    String endDate = request.getParameter("endDate");

    String anyText = LocaleUtils.getLocalizedString("archive.settings.any", "monitoring");

    int start = 0;
    int range = 15;
    int numPages = 1;
    int curPage = (start / range) + 1;

    if (anyText.equalsIgnoreCase(participant1)) {
        participant1 = null;
    }

    if (anyText.equalsIgnoreCase(participant2)) {
        participant2 = null;
    }

    if (anyText.equalsIgnoreCase(startDate)) {
        startDate = null;
    }

    if (anyText.equalsIgnoreCase(endDate)) {
        endDate = null;
    }

    if (submit) {
        ArchiveSearch search = new ArchiveSearch();
        JID participant1JID = null;
        JID participant2JID = null;

        String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        if (participant1 != null && participant1.length() > 0) {
            int position = participant1.lastIndexOf("@");
            if (position > -1) {
                String node = participant1.substring(0, position);
                participant1JID = new JID(JID.escapeNode(node) + participant1.substring(position));
            } else {
                participant1JID = new JID(JID.escapeNode(participant1), serverName, null);
            }
        }

        if (participant2 != null && participant2.length() > 0) {
            int position = participant2.lastIndexOf("@");
            if (position > -1) {
                String node = participant2.substring(0, position);
                participant2JID = new JID(JID.escapeNode(node) + participant2.substring(position));
            } else {
                participant2JID = new JID(JID.escapeNode(participant2), serverName, null);
            }
        }

        if (startDate != null && startDate.length() > 0) {
            DateFormat formatter;
            if (startDate.contains("/")) {
                // This was used by the old calendarjs code. Retain it to not break old links/bookmarks, etc.
                formatter = new SimpleDateFormat("MM/dd/yy");
            } else {
                formatter = new SimpleDateFormat("yyyy-MM-dd");
            }
            try {
                Date date = formatter.parse(startDate);
                search.setDateRangeMin(date);
            }
            catch (Exception e) {
                // TODO: mark as an error in the JSP instead of logging..
                logger.error("Date range can't be established", e);
            }
        }

        if (endDate != null && endDate.length() > 0) {
            DateFormat formatter;
            if (endDate.contains("/")) {
                // This was used by the old calendarjs code. Retain it to not break old links/bookmarks, etc.
                formatter = new SimpleDateFormat("MM/dd/yy");
            } else {
                formatter = new SimpleDateFormat("yyyy-MM-dd");
            }
            try {
                Date date = formatter.parse(endDate);
                // The user has chosen an end date and expects that any conversation
                // that falls on that day will be included in the search results. For
                // example, say the user choose 6/17/2006 as an end date. If a conversation
                // occurs at 5:33 PM that day, it should be included in the results. In
                // order to make this possible, we need to make the end date one millisecond
                // before the next day starts.
                date = new Date(date.getTime() + JiveConstants.DAY - 1);
                search.setDateRangeMax(date);
            }
            catch (Exception e) {
                // TODO: mark as an error in the JSP instead of logging..
                logger.error("Date range can't be established", e);
            }
        }

        if (query != null && query.length() > 0) {
            search.setQueryString(query);
        }

        if (participant1JID != null && participant2JID != null) {
            search.setParticipants(participant1JID, participant2JID);
        } else if (participant1JID != null) {
            search.setParticipants(participant1JID);
        } else if (participant2JID != null) {
            search.setParticipants(participant2JID);
        }

        start = ParamUtils.getIntParameter(request, "start", 0);
        range = 15;


        conversations = archiveSearcher.search(search);

        numPages = (int) Math.ceil((double) conversations.size() / (double) range);
        curPage = (start / range) + 1;
    }

    boolean isArchiveEnabled = conversationManager.isArchivingEnabled();
%>

<html>
<head>
<title><fmt:message key="archive.search.title" /></title>
<meta name="pageID" content="archive-search"/>
<script type="text/javascript" language="javascript" src="scripts/tooltips/domLib.js"></script>
<script type="text/javascript" language="javascript" src="scripts/tooltips/domTT.js"></script>

<script type="text/javascript">
    function hover(oRow) {
        oRow.style.background = "#A6CAF0";
        oRow.style.cursor = "pointer";
    }

    function noHover(oRow) {
        oRow.style.background = "white";
    }

    function viewConversation(conversationID) {
        window.frames['view'].location.href = "conversation-viewer.jsp?conversationID=" + conversationID;
    }

    function submitFormAgain(start, range){
        document.f.start.value = start;
        document.f.range.value = range;
        document.f.parseRange.value = "true";
        document.f.submit();
    }
</script>
<style type="text/css">
    .small-label {
        font-size: 11px;
        font-weight: bold;
        font-family: Verdana, Arial, sans-serif;
    }

    .small-label-no-bold {
        font-size: 11px;
        font-family: Verdana, Arial, sans-serif;
    }


    .small-label-with-padding {
        font-size: 12px;
        font-weight: bold;
        font-family: Verdana, Arial, sans-serif;
    }


    .small-text {
        font-size: 11px;
        font-family: Verdana, Arial, sans-serif;
        line-height: 11px;
    }

    .very-small-label {
        font-size: 10px;
        font-weight: bold;
        font-family: Verdana, Arial, sans-serif;
        padding-right:5px;
    }


    .stat {
        margin: 0 0 8px 0;
        border: 1px solid #cccccc;
        -moz-border-radius: 3px;
    }

    .stat td table {
        margin: 5px 10px 5px 10px;
    }
    .stat div.verticalrule {
        display: block;
        width: 1px;
        height: 110px;
        background-color: #cccccc;
        overflow: hidden;
        margin-left: 3px;
        margin-right: 3px;
    }

    .conversation-body {
        color: black;
        font-size: 11px;
        font-family: Verdana, Arial, sans-serif;
    }

    .conversation-label1 {
        color: blue;
        font-size: 10px;
        font-family: Verdana, Arial, sans-serif;
    }

    .conversation-label2 {
        color: red;
        font-size: 10px;
        font-family: Verdana, Arial, sans-serif;
    }

    .conversation-label3 {
        color: orchid;
        font-size: 10px;
        font-family: Verdana, Arial, sans-serif;
    }

    .conversation-label4 {
        color: black;
        font-size: 10px;
        font-family: Verdana, Arial, sans-serif;
    }

    .conversation-table {
        font-family: Verdana, Arial, sans-serif;
        font-size: 11px;
    }
    .conversation-table td {
        font-size: 11px;
        padding: 5px 5px 5px 5px;
    }

    .light-gray-border {
        border-color: #bbb;
        border-style: solid;
        border-width: 1px 1px 1px 1px;
    }

    .light-gray-border-bottom {
        border-color: #bbb;
        border-style: solid;
        border-width: 0 0 1px 0;
    }

    .small-description {
        font-size: 11px;
        font-family: Verdana, Arial, sans-serif;
        color: #666;
    }

   .description {
        font-size: 12px;
        font-family: Verdana, Arial, sans-serif;
        color: #666;
    }


      .pagination {
        border-color: #bbb;
        border-style: solid;
        border-width: 0 0 1px 0;
        font-size: 10px;
        font-family: Verdana, Arial, sans-serif;

    }

    .content {
        border-color: #bbb;
        border-style: solid;
        border-width: 0 0 1px 0;
    }

    /* Default DOM Tooltip Style */
    div.domTT {
        border: 1px solid #bbb;
        background-color: #FFFBE2;
        font-family: Arial, Helvetica sans-serif;
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
        font-family: Verdana, Arial, sans-serif;
        height: 20px;
        background: #efefef;
    }

    .keyword-field {
        font-size: 11px;
        font-family: Verdana, Arial, sans-serif;
        height: 20px;
    }

    #searchResults {
        margin: 10px 0 10px 0;
    }

    #searchResults h3 {
        font-size: 14px;
        padding: 0;
        margin: 0 0 2px 0;
        color: #555555;
    }

    #searchResults p.resultDescription {
        margin: 0 0 12px 0;
    }
</style>

<script type="text/javascript">
    var selectedConversation;

    function showConversation(conv) {
        selectedConversation = conv;

        let xhr = new XMLHttpRequest();
        xhr.onload = function () {
            if (xhr.readyState === 4 && xhr.status === 200) {
                showConv(JSON.parse(xhr.responseText));
            }
        };
        xhr.open("GET", '/plugins/monitoring/api/conversations/' +conv);
        xhr.send(null);
    }

    function showConv(results) {
        document.getElementById('chat-viewer-empty').style.display = 'none';
        document.getElementById('chat-viewer').style.display = '';
        if (results.allParticipants != null) {
            document.getElementById('con-participant1').innerHTML = results.allParticipants.length;
            document.getElementById('con-participant2').innerHTML = '(<a href="#" onclick="showOccupants(' + results.conversationID + ', 0);return false;">view</a>)';
        }
        else {
            document.getElementById('con-participant1').innerHTML = results.participant1 + ',';
            document.getElementById('con-participant2').innerHTML = results.participant2;
        }
        document.getElementById('con-chatTime').innerHTML = results.date;
        document.getElementById('conversation-body').innerHTML = results.body;
        document.getElementById('con-noMessages').innerHTML = results.messageCount;
        document.getElementById('con-duration').innerHTML = results.duration;
        <% if (conversationManager.isArchivingEnabled()) { %>
            document.getElementById('con-chat-link').innerHTML = '<a href="conversation?conversationID='+selectedConversation+'" class="very-small-label"  style="text-decoration:none" target=_blank>View PDF</a>';
        <% } else { %>
            document.getElementById('pdf-image').style.display = 'none';
        <% } %>
    }

    function showOccupants(conversationID, start) {
        var xhr = new XMLHttpRequest();
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4 && xhr.status === 200)
                showOcc(xhr.responseText);
        }
        xhr.open("GET", 'archive-conversation-participants.jsp?conversationID=' + conversationID + '&start=' + start, true);
        xhr.send(null);
    }

    function showOcc(result) {
        var occupantsDialog = document.getElementById('occupants');
        if (typeof occupantsDialog.showModal === "function") {
            occupantsDialog.innerHTML = result;
            occupantsDialog.showModal();
        } else {
            alert("The <dialog> API is not supported by this browser.");
        }
    }

    function grayOut(ele) {
        if (ele.value === 'Any') {
            ele.style.backgroundColor = "#FFFBE2";
        }
        else {
            ele.style.backgroundColor = "#ffffff";
        }
    }
    
  //# sourceURL=archive-search.jsp
</script>
<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function(event) {
        let textFieldElements = document.getElementsByClassName('textfield');
        for (var i = 0; i < textFieldElements.length; ++i) {
            let el = textFieldElements[i];
            el.getcla
            el.onblur = function() {
                let va = el.value;
                if (va.length === 0 || va === 'Any' || va === 'any') {
                    this.style.backgroundColor = '#efefef';
                    if (el.classList.contains('datefield')) {
                        el.type = 'text';
                    }
                    el.value = "<%= anyText%>";
                }
                else {
                    this.style.backgroundColor = '#ffffff';
                }
            }

            el.onfocus = function() {
                let va = el.value;
                if (va === "<%= anyText%>") {
                    this.style.backgroundColor = '#ffffff';
                    el.value = "";
                    if (el.classList.contains('datefield')) {
                        el.type = 'date';
                    }
                }
            }
        }
    });
</script>
<style type="text/css">
    @import "style/style.css";

    /* Add a nice little rollover effect to any row in a jive-table object. This will help visually link left and right columns. */
    .conversation-table tr:hover{
        background: #dedede;
        cursor: pointer;
    }
</style>
</head>
<body>

<% if (!ClusterManager.findRemotePluginsWithDifferentVersion(MonitoringConstants.PLUGIN_NAME).isEmpty()) { %>
<div class="warning">
    <fmt:message key="warning.clustering.versions">
        <fmt:param value="<a href='/system-clustering.jsp'>" />
        <fmt:param value="</a>" />
    </fmt:message>
</div>
<br/>
<% } %>

<a href="archive-conversation-participants.jsp?conversationID=" id="lbmessage" title="<fmt:message key="archive.group_conversation.participants" />" style="display:none;"></a>

<dialog id="occupants"></dialog>

<form action="archive-search.jsp" name="f">
<!-- Search Table -->
<div>
<table class="stat">
<tr valign="top">
<td>
    <table>
        <tr>
            <td colspan="3">
                <img src="images/icon_participants.gif" align="absmiddle" alt="" style="margin-right: 4px;"/>
                <b><fmt:message key="archive.search.participants" /></b>
                <a onmouseover="domTT_activate(this, event, 'content',
                    '<fmt:message key="archive.search.participants.tooltip"/>',
                    'trail', true, 'direction', 'northeast', 'width', '220');"><img src="images/icon_help_14x14.gif" alt="" vspace="2" align="texttop"/></a>
            </td>
        </tr>
        <tr>
            <td>
                <input type="text" size="22" name="participant1" value="<%= participant1 != null ? StringUtils.escapeForXML(participant1) :
                LocaleUtils.getLocalizedString("archive.search.participants.any", "monitoring") %>" class="textfield"/>
            </td>

        </tr>
        <tr>
            <td>
                <input type="text" size="22" name="participant2" value="<%= participant2 != null ? StringUtils.escapeForXML(participant2) : anyText %>" class="textfield"/>
            </td>

        </tr>
    </table>
</td>
<td width="0" height="100%" valign="middle">
    <div class="verticalrule"></div>
</td>
<td>

    <table>
        <tr>
            <td colspan="2">
                <img src="images/icon_daterange.gif" align="absmiddle" alt="" style="margin: 0 4px 0 2px;"/>
                <b><fmt:message key="archive.search.daterange" /></b>
                <a onmouseover="domTT_activate(this, event, 'content',
                    '<fmt:message key="archive.search.daterange.tooltip"/>',
                    'trail', true, 'direction', 'northeast', 'width', '220');"><img src="images/icon_help_14x14.gif" vspace="2" align="texttop"/></a>
            </td>
        </tr>
        <tr valign="top">
            <td><fmt:message key="archive.search.daterange.start" /></td>
            <td>
                <input type="<%= startDate != null ? "date" : "text" %>" id="startDate" name="startDate" size="13"
                       value="<%= startDate != null ? StringUtils.escapeForXML(startDate) :
                       LocaleUtils.getLocalizedString("archive.search.daterange.any", "monitoring")%>" class="textfield datefield"/><br/>
            </td>
        </tr>
        <tr valign="top">
            <td><fmt:message key="archive.search.daterange.end" /></td>
            <td>
                <input type="<%= startDate != null ? "date" : "text" %>" id="endDate" name="endDate" size="13"
                       value="<%= endDate != null ? StringUtils.escapeForXML(endDate) :
                       LocaleUtils.getLocalizedString("archive.search.daterange.any", "monitoring") %>" class="textfield datefield"/><br/>
            </td>
        </tr>
    </table>


</td>
<td>
    <td width="0" height="100%" valign="middle">
        <div class="verticalrule"></div>
    </td>
</td>
<td>
    <table>
        <tr valign="top">
            <td>
                <img src="images/icon_keywords.gif" align="absmiddle" alt="" style="margin-right: 4px;"/>
                <b><fmt:message key="archive.search.keywords" /></b> <fmt:message key="archive.search.keywords.optional" />
            </td>
        </tr>
        <tr>
            <td>
                <% if(isArchiveEnabled){%>
                <input type="text" name="keywords" size="35" class="keyword-field" value="<%= query != null ? StringUtils.escapeForXML(query) : ""%>"/>
                <% } else { %>
                    <fmt:message key="archive.search.keywords.disabled">
                        <fmt:param value="<a href='archiving-settings.jsp'>" />
                        <fmt:param value="</a>" />
                    </fmt:message>
                <% } %>
            </td>
        </tr>
    </table>
</td>
</tr>
</table>
</div>
<input type="submit" name="submitForm" value="<fmt:message key="archive.search.submit" />" class="small-text"/>


<input type="hidden" name="start"  />
<input type="hidden" name="range"  />
<input type="hidden" name="parseRange" />
</form>

<%
    // Code for the searches.

%>

<% if (conversations != null && conversations.size() > 0) { %>
<table id="searchResults" width="100%" style="<%= conversations == null ? "display:none;" : "" %>">
    <tr>
        <td colspan="2">
            <h3><fmt:message key="archive.search.results" /> <%= conversations.size() %></h3>
            <p class="resultDescription">
                <fmt:message key="archive.search.results.description">
                    <fmt:param value="<%= conversations.size()%>" />
                </fmt:message>
            </p>
        </td>
    </tr>
    <tr valign="top">
        <td width="300">
            <!-- Search Result Table -->
            <table cellspacing="0" class="light-gray-border">
                <tr class="light-gray-border-bottom">
                    <td class="light-gray-border-bottom">
                        <%
                            int endPoint = (start + range) > conversations.size() ? conversations.size() : (start + range);
                        %>
                        <span class="small-label-with-padding">
                            <%= start + 1%> - <%= endPoint %> <fmt:message key="archive.search.results.xofy" />
                            <%= conversations.size()%></span>
                    </td>
                    <td align="right" nowrap class="light-gray-border-bottom" style="padding-right:3px;">
                          <%  if (numPages > 1) { %>

                        <p>
                            <%  int num = 5 + curPage;
                                int s = curPage - 1;
                                if (s > 5) {
                                    s -= 5;
                                }
                                if (s < 5) {
                                    s = 0;
                                }
                                if (s > 2) {
                            %>
                            <a href="javascript:submitFormAgain('0', '<%= range%>');">1</a> ...

                            <%
                                }
                                int i = 0;
                                for (i = s; i < numPages && i < num; i++) {
                                    String sep = ((i + 1) < numPages) ? " " : "";
                                    boolean isCurrent = (i + 1) == curPage;
                            %>
                            <a href="javascript:submitFormAgain('<%= (i*range) %>', '<%= range %>');"
                               class="<%= ((isCurrent) ? "small-label" : "small-label-no-bold") %>"
                                ><%= (i + 1) %></a><%= sep %>

                            <%  } %>

                            <%  if (i < numPages) { %>

                            ... <a href="javascript:submitFormAgain('<%= ((numPages-1)*range) %>', '<%= range %>');"><%= numPages %></a>

                            <%  } %>
                        </p>

                        <%  } else { %>
                        &nbsp;
                        <%  } %>

                    </td>
                </tr>
                <tr>
                    <td colspan="2" align="left">
                        <div style="HEIGHT:300px;width:285px;OVERFLOW:auto">
                            <table cellpadding="3" cellspacing="0" width="100%" class="conversation-table">

                                <%
                                    int i = 1;
                                    int end = start + range + 1;
                                    for (Conversation conversation : conversations) {
                                        if(i == end){
                                            break;
                                        }
                                        else if(i < start){
                                            i++;
                                            continue;
                                        }
                                        Map<String, JID> participants = getParticipants(conversation);
                                        String color = "#FFFFFF";
                                        if (i % 2 == 0) {
                                            color = "#F0F0F0";
                                        }

                                %>
                                <tr id="<%= conversation.getConversationID()%>" valign="top" bgcolor="<%= color%>" onclick="showConversation('<%= conversation.getConversationID() %>'); return false;">
                                    <td><b><%= i %>.</b></td>
                                    <td width="98%">
                                        <% if (conversation.getRoom() == null) { %>
                                            <%
                                                Iterator iter = participants.keySet().iterator();
                                                while (iter.hasNext()) {
                                                    String name = (String)iter.next();
                                            %>
                                            <%= name%><br/>
                                            <% } %>
                                        <% } else { %>
                                            <i><fmt:message key="archive.search.group_conversation">
                                                <fmt:param value="<%= conversation.getRoom().getNode() %>" />
                                            </fmt:message></i><br>
                                            <fmt:message key="archive.search.results.participants" /> <%= conversation.getParticipants().size() %>
                                        <% } %>
                                    </td>
                                    <td align="right" nowrap>
                                        <%= getFormattedDate(conversation)%>
                                    </td>
                                </tr>
                                <% i++;
                                } %>
                            </table>
                        </div>
                    </td>
                </tr>
            </table>
        </td>
        <td>


             <!-- Conversation Viewer (empty) -->
            <div id="chat-viewer-empty">
                <table class="light-gray-border" width="100%" style="height: 323px;">
                    <tr>
                        <td align="center" valign="top" bgcolor="#fafafa">
                            <br>
                            <p>Select a conversation to the left to view details.</p></td>
                    </tr>
                </table>
            </div>

            <!-- Conversation Viewer -->
            <div id="chat-viewer" style="display:none;">
                <table class="light-gray-border" cellspacing="0">
                    <tr valign="top">
                        <td width="99%" bgcolor="#f0f0f0" class="light-gray-border-bottom" style="padding: 3px 2px 4px 5px;">
                            <span class="small-label"><fmt:message key="archive.search.results.participants" /></span>&nbsp;
                            <span class="small-text" id="con-participant1"></span>&nbsp;
                            <span class="small-text" id="con-participant2"></span><br/>
                            <span class="small-label"><fmt:message key="archive.search.results.messagecount" /></span>&nbsp;
                            <span class="small-text" id="con-noMessages"></span><br/>
                            <span class="small-label"><fmt:message key="archive.search.results.date" /></span>&nbsp;
                            <span class="small-text" id="con-chatTime"></span><br/>
                            <span class="small-label"><fmt:message key="archive.search.results.duration" /></span>&nbsp;
                            <span class="small-text" id="con-duration"></span>
                        </td>
                        <td id="pdf-image" width="1%" bgcolor="#f0f0f0" nowrap align="right" class="light-gray-border-bottom" style="padding: 4px 3px 3px 0;">
                            <img src="images/icon_pdf.gif" alt="" align="texttop" border="0" /> <span id="con-chat-link"></span>
                        </td>

                    </tr>
                    <tr>
                        <td colspan="2">
                            <div class="conversation" id="conversation-body" style="HEIGHT:241px;width:100%;OVERFLOW:auto">
                            </div>
                        </td>
                    </tr>
                </table>
            </div>


        </td>
    </tr>
</table>

<% } else if(submit) { %>
<span class="description">
<fmt:message key="archive.search.results.none" />
</span>
<% } %>


<script type="text/javascript">
    grayOut(f.participant1);
    grayOut(f.participant2);
    grayOut(f.startDate);
    grayOut(f.endDate);

     function catcalc(cal) {
        var endDateField = document.getElementById('endDate');
        var startDateField = document.getElementById('startDate');

        var endTime = new Date(endDateField.value);
        var startTime = new Date(startDateField.value);
        if(endTime.getTime() < startTime.getTime()){
            alert("<fmt:message key="archive.search.daterange.error" />");
            startDateField.value = "<fmt:message key="archive.search.daterange.any" />";
            startDateField.type = 'text';
        }
    }
</script>
</body>
</html>

<%!
    public TreeMap<String, JID> getParticipants(Conversation conv) {
        final TreeMap<String, JID> participants = new TreeMap<String, JID>();
        for (JID jid : conv.getParticipants()) {
            try {
                if (jid == null) {
                    continue;
                }
                String identifier = jid.toBareJID();
                try {
                    identifier = UserNameManager.getUserName(jid, jid.toBareJID());
                } catch (UserNotFoundException e) {
                    // Ignore
                }
                participants.put(identifier, jid);
            }
            catch (Exception e) {
                LoggerFactory.getLogger("archive-search-jsp").error("Participants can't be collected", e);
            }

        }

        return participants;
    }

    public String getFormattedDate(Conversation conv) {
        return JiveGlobals.formatDate(conv.getStartDate());
    }
%>
