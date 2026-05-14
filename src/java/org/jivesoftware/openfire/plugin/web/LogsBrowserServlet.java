package org.jivesoftware.openfire.plugin.web;

import com.reucon.openfire.plugin.archive.impl.MucMamPersistenceManager;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side browser for public room logs.
 *
 * <p>This servlet renders one HTML page per URL level:</p>
 * <ul>
 *     <li><code>/logs/</code>: select a service.</li>
 *     <li><code>/logs/{service}/</code>: select a room.</li>
 *     <li><code>/logs/{service}/{room}/</code>: select a date.</li>
 *     <li><code>/logs/{service}/{room}/{yyyy-MM-dd}</code>: view messages.</li>
 * </ul>
 */
public class LogsBrowserServlet extends HttpServlet
{
    public static final SystemProperty<Boolean> PROP_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("archive.settings.logapi.enabled" )
        .setDefaultValue( false )
        .setDynamic( true )
        .setPlugin(MonitoringConstants.PLUGIN_NAME)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(LogsBrowserServlet.class);
    private static final String TEMPLATE_PATH = "/WEB-INF/templates/logs-browser.html";
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    protected void doGet( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        if (isStaticAssetRequest(request)) {
            forwardToDefaultServlet(request, response);
            return;
        }

        final String basePath = request.getContextPath();

        if (!PROP_ENABLED.getValue()) {
            renderError(response, HttpServletResponse.SC_FORBIDDEN, basePath, "Public log browsing is disabled.", "Enable archive.settings.logapi.enabled to expose public logs.");
            return;
        }

        final List<String> segments = splitAndDecodePath(request.getPathInfo());

        switch (segments.size()) {
            case 0:
                renderServiceSelection(response, basePath);
                return;
            case 1:
                renderRoomSelection(response, basePath, segments.get(0));
                return;
            case 2:
                renderDateSelection(response, basePath, segments.get(0), segments.get(1));
                return;
            case 3:
                renderMessageLog(request, response, basePath, segments.get(0), segments.get(1), segments.get(2));
                return;
            default:
                renderError(response, HttpServletResponse.SC_NOT_FOUND, basePath, "Unknown URL.", "Use /logs/, /logs/{service}/, /logs/{service}/{room}/, or /logs/{service}/{room}/{date}.");
        }
    }

    @Override
    protected void doPost( final HttpServletRequest request, final HttpServletResponse response ) throws IOException
    {
        final String basePath = request.getContextPath();
        if (!PROP_ENABLED.getValue()) {
            renderError(response, HttpServletResponse.SC_FORBIDDEN, basePath, "Public log browsing is disabled.", "Enable archive.settings.logapi.enabled to expose public logs.");
            return;
        }

        final String intent = safeTrim(request.getParameter("intent"));
        if ("service".equals(intent)) {
            final String service = safeTrim(request.getParameter("service"));
            if (service.isEmpty()) {
                response.sendRedirect(basePath + "/");
                return;
            }
            response.sendRedirect(basePath + "/" + encodePathSegment(service) + "/");
            return;
        }

        if ("room".equals(intent)) {
            final String service = safeTrim(request.getParameter("service"));
            final String room = safeTrim(request.getParameter("room"));
            if (service.isEmpty() || room.isEmpty()) {
                response.sendRedirect(basePath + "/");
                return;
            }
            response.sendRedirect(basePath + "/" + encodePathSegment(service) + "/" + encodePathSegment(room) + "/");
            return;
        }

        if ("date".equals(intent)) {
            final String service = safeTrim(request.getParameter("service"));
            final String room = safeTrim(request.getParameter("room"));
            final String date = safeTrim(request.getParameter("date"));
            if (service.isEmpty() || room.isEmpty() || date.isEmpty()) {
                response.sendRedirect(basePath + "/");
                return;
            }

            try {
                LocalDate.parse(date, ISO_LOCAL_DATE);
            } catch (DateTimeParseException ex) {
                response.sendRedirect(basePath + "/" + encodePathSegment(service) + "/" + encodePathSegment(room) + "/");
                return;
            }

            response.sendRedirect(basePath + "/" + encodePathSegment(service) + "/" + encodePathSegment(room) + "/" + date);
            return;
        }

        response.sendRedirect(basePath + "/");
    }

    /**
     * A servlet mapped to <code>/*</code> also receives static-resource requests.
     * Delegate those to Jetty's default servlet.
     */
    private boolean isStaticAssetRequest( final HttpServletRequest request )
    {
        final String pathInfo = request.getPathInfo();
        return pathInfo != null && pathInfo.contains(".");
    }

    /**
     * Delegate static-resource requests to the container's default servlet.
     */
    private void forwardToDefaultServlet( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        final javax.servlet.RequestDispatcher dispatcher = getServletContext().getNamedDispatcher("default");
        if (dispatcher == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        dispatcher.forward(request, response);
    }

    /**
     * Render the top-level page with all services that expose public room logs.
     */
    private void renderServiceSelection( final HttpServletResponse response, final String basePath ) throws IOException
    {
        final List<String> services = getPublicServices();

        if (services.size() == 1) {
            response.sendRedirect(basePath + "/" + encodePathSegment(services.get(0)) + "/");
            return;
        }

        final String content = services.isEmpty()
            ? "<p>No services with publicly available room logs were found.</p>"
            : renderServiceForm(basePath, services);
        renderPage(response, HttpServletResponse.SC_OK, basePath, "Select a conference service", null, null, content);
    }

    /**
     * Render one service page that lists publicly logged rooms.
     */
    private void renderRoomSelection( final HttpServletResponse response, final String basePath, final String serviceName ) throws IOException
    {
        final MultiUserChatService service = getService(serviceName);
        if (service == null) {
            renderError(response, HttpServletResponse.SC_NOT_FOUND, basePath, "Unknown service", "No service named '" + escapeHtml(serviceName) + "' exists.");
            return;
        }

        final List<RoomOption> rooms = getPublicRooms(service);
        final String content = rooms.isEmpty()
            ? "<p>No publicly logged rooms were found in this service.</p>"
            : renderRoomForm(basePath, serviceName, rooms);
        renderPage(response, HttpServletResponse.SC_OK, basePath, "Select a room in service '" + escapeHtml(serviceName) + "'", basePath,
            "Back to services", content);
    }

    /**
     * Render one room page that lists selectable UTC dates.
     */
    private void renderDateSelection( final HttpServletResponse response, final String basePath, final String serviceName, final String roomName ) throws IOException
    {
        final MUCRoom room = getPublicRoom(serviceName, roomName);
        if (room == null) {
            renderError(response, HttpServletResponse.SC_NOT_FOUND, basePath, "Unknown room", "No publicly logged room named '" + escapeHtml(roomName) + "' was found in service '" + escapeHtml(serviceName) + "'.");
            return;
        }

        final List<String> dates = getDatesForRoom(room);
        final String content = dates.isEmpty()
            ? "<p>No messages were found for this room.</p>"
            : renderDatePickerForm(basePath, serviceName, roomName, dates);
        renderPage(response, HttpServletResponse.SC_OK, basePath, "Select a date for '" + escapeHtml(roomName) + "'",
            basePath + "/" + encodePathSegment(serviceName) + "/", "Back to rooms", content);
    }

    /**
     * Render the message table for one room on one date.
     */
    private void renderMessageLog( final HttpServletRequest request, final HttpServletResponse response, final String basePath, final String serviceName, final String roomName, final String date ) throws IOException
    {
        final MUCRoom room = getPublicRoom(serviceName, roomName);
        if (room == null) {
            renderError(response, HttpServletResponse.SC_NOT_FOUND, basePath, "Unknown room", "No publicly logged room named '" + escapeHtml(roomName) + "' was found in service '" + escapeHtml(serviceName) + "'.");
            return;
        }

        final Instant dayStart;
        final LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date, ISO_LOCAL_DATE);
            dayStart = parsedDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ex) {
            renderError(response, HttpServletResponse.SC_BAD_REQUEST, basePath, "Invalid date format", "Use an ISO date such as 2026-05-13.");
            return;
        }

        final HighlightRange highlightRange = parseHighlightRange(request);
        final List<LogsBrowserServlet.Message> messages = getMessages(dayStart, dayStart.plus(1, ChronoUnit.DAYS), room);
        final List<String> dates = getDatesForRoom(room);
        final String dayNavigation = renderDayNavigation(basePath, serviceName, roomName, date, dates, request.getLocale());
        final String content = dayNavigation
            + renderMessagesTable(basePath, serviceName, roomName, date, messages, highlightRange)
            + dayNavigation;
        final String humanDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(request.getLocale())
            .format(parsedDate);
        renderPage(response, HttpServletResponse.SC_OK, basePath,
            "Logs for '" + escapeHtml(roomName) + "' on " + escapeHtml(humanDate),
            basePath + "/" + encodePathSegment(serviceName) + "/" + encodePathSegment(roomName) + "/", "Back to dates", content);
    }

    /**
     * Return all MUC services that expose at least one room with logs.
     */
    private List<String> getPublicServices()
    {
        return XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices().stream()
            .filter(service -> !getPublicRooms(service).isEmpty())
            .map(MultiUserChatService::getServiceName)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Resolve one MUC service by name.
     */
    private MultiUserChatService getService( final String serviceName )
    {
        return XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
    }

    /**
     * Resolve one public, log-enabled room by service and room name.
     */
    private MUCRoom getPublicRoom( final String serviceName, final String roomName )
    {
        final MultiUserChatService service = getService(serviceName);
        if (service == null) {
            return null;
        }

        final MUCRoom room = service.getChatRoom(roomName);
        if (room == null || !room.isPublicRoom() || !room.isLogEnabled()) {
            return null;
        }

        return room;
    }

    /**
     * Return publicly logged rooms with their names and descriptions.
     */
    private List<RoomOption> getPublicRooms( final MultiUserChatService service )
    {
        if (service == null) {
            return Collections.emptyList();
        }

        //TODO: https://github.com/igniterealtime/openfire-monitoring-plugin/issues/228
        return service.getActiveChatRooms().stream()
            .filter(Objects::nonNull)
            .filter(room -> room.isPublicRoom() && room.isLogEnabled())
            .filter(this::hasLogs)
            .map(room -> new RoomOption(room.getName(), room.getDescription()))
            .sorted(Comparator.comparing(RoomOption::getName))
            .collect(Collectors.toList());
    }

    /**
     * Verify that a room has at least one archived message.
     */
    private boolean hasLogs( final MUCRoom room )
    {
        try {
            return MucMamPersistenceManager.hasLoggedMessage(room);
        } catch (RuntimeException e) {
            // Ignore malformed archive rows and keep the room hidden from public browsing.
            Log.warn("Unable to determine if room '{}' has logs. Hiding room from list.", room, e);
            return false;
        }
    }

    /**
     * Builds a day list from first to last message date (UTC), mirroring existing REST behavior.
     */
    private List<String> getDatesForRoom( final MUCRoom room )
    {
        final Instant start;
        final Instant end;
        try {
            start = MucMamPersistenceManager.getDateOfFirstLog(room);
            end = MucMamPersistenceManager.getDateOfLastLog(room);
        } catch (RuntimeException e) {
            // Be defensive: malformed/empty archive rows should not break public browsing.
            Log.warn("Unable to determine available log dates for room '{}'. Returning no dates.", room, e);
            return Collections.emptyList();
        }

        if (start == null || end == null) {
            return Collections.emptyList();
        }

        final List<String> dates = new ArrayList<>();
        Instant needle = start;
        while (!needle.isAfter(end)) {
            dates.add(ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(needle));
            needle = needle.plus(1, ChronoUnit.DAYS);
        }
        return dates;
    }

    public static List<LogsBrowserServlet.Message> getMessages(Instant after, Instant before, MUCRoom room )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<LogsBrowserServlet.Message> msgs = new LinkedList<>();
        try {
            connection = DbConnectionManager.getConnection();
            pstmt = connection.prepareStatement( "SELECT sender, nickname, logTime, body from ofMucConversationLog WHERE roomID = ? AND logTime >= ? AND logTime < ? ORDER BY logTime ASC" );
            pstmt.setLong( 1, room.getID() );
            pstmt.setString(2, StringUtils.dateToMillis(Date.from(after) ) ); // inclusive
            pstmt.setString( 3, StringUtils.dateToMillis( Date.from(before) ) ); // exclusive
            rs = pstmt.executeQuery();
            final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone( ZoneOffset.UTC );
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String body = rs.getString(4);

                final LogsBrowserServlet.Message message = new LogsBrowserServlet.Message();
                message.setTimestamp( formatter.format( sentDate.toInstant() ) );
                message.setNickname( nickname != null && !nickname.isEmpty() ? nickname : new JID(senderJID).getResource());
                message.setMessage( body );
                msgs.add( message );
            }
        } catch ( SQLException e) {
            Log.error("SQL failure during MAM-MUC message retrieval for room {} between {} and {} ", room, after, before, e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, connection);
        }

        return msgs;
    }

    /**
     * Split path info into decoded URL path segments.
     */
    private static List<String> splitAndDecodePath( final String pathInfo )
    {
        if (pathInfo == null || pathInfo.isEmpty() || "/".equals(pathInfo)) {
            return Collections.emptyList();
        }

        final String normalized = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        final String[] rawSegments = normalized.split("/");
        final List<String> result = new ArrayList<>();
        for (final String segment : rawSegments) {
            if (!segment.isEmpty()) {
                result.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    /**
     * Encode one value for safe usage as a URL path segment.
     */
    private static String encodePathSegment( final String value )
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Format an ISO instant into HH:mm:ss (UTC) for table display.
     */
    private static String formatUtcTime( final String timestamp )
    {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }

        try {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.parse(timestamp)).substring(11, 19);
        } catch (Exception e) {
            return timestamp;
        }
    }

    /**
     * Render an error message using the shared HTML template.
     */
    private void renderError( final HttpServletResponse response, final int status, final String basePath,
                              final String title, final String message ) throws IOException
    {
        renderPage(response, status, basePath, title, null, null, "<p class=\"error\">" + message + "</p>");
    }

    /**
     * Render one page by applying content into a static HTML template.
     */
    private void renderPage( final HttpServletResponse response, final int status, final String basePath,
                             final String title, final String backUrl, final String backLabel,
                             final String contentHtml ) throws IOException
    {
        final String template = loadTemplate();
        final String backLink = (backUrl == null || backLabel == null)
            ? ""
            : "<p><a href=\"" + escapeHtml(backUrl) + "\">" + escapeHtml(backLabel) + "</a></p>";

        final String rendered = template
            .replace("{{TITLE}}", escapeHtml(title))
            .replace("{{CONTEXT_PATH}}", escapeHtml(basePath))
            .replace("{{BACK_LINK}}", backLink)
            .replace("{{CONTENT}}", contentHtml);

        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(rendered);
    }

    /**
     * Read the static HTML template from the plugin web resources.
     */
    private String loadTemplate() throws IOException
    {
        try (InputStream in = getServletContext().getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IOException("Missing template: " + TEMPLATE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Render a server-side service selector form.
     */
    private String renderServiceForm( final String basePath, final List<String> services )
    {
        final StringBuilder result = new StringBuilder();
        result.append("<form class=\"selector-form\" method=\"post\" action=\"")
            .append(escapeHtml(basePath))
            .append("/\">")
            .append("<input type=\"hidden\" name=\"intent\" value=\"service\" />")
            .append("<label for=\"service\">Conference service</label>")
            .append("<select id=\"service\" name=\"service\" required>")
            .append("<option value=\"\">Choose a service...</option>");
        for (final String service : services) {
            result.append("<option value=\"")
                .append(escapeHtml(service))
                .append("\">")
                .append(escapeHtml(service))
                .append("</option>");
        }
        result.append("</select>")
            .append("<button type=\"submit\">Continue</button>")
            .append("</form>");
        return result.toString();
    }

    /**
     * Render a server-side room selector form.
     */
    private String renderRoomForm( final String basePath, final String serviceName, final List<RoomOption> rooms )
    {
        final StringBuilder result = new StringBuilder();
        result.append("<form class=\"selector-form\" method=\"post\" action=\"")
            .append(escapeHtml(basePath))
            .append("/\">")
            .append("<input type=\"hidden\" name=\"intent\" value=\"room\" />")
            .append("<input type=\"hidden\" name=\"service\" value=\"")
            .append(escapeHtml(serviceName))
            .append("\" />")
            .append("<label for=\"room\">Room</label>")
            .append("<select id=\"room\" name=\"room\" required>")
            .append("<option value=\"\">Choose a room...</option>");
        for (final RoomOption room : rooms) {
            final String roomLabel = room.getDescription().isEmpty()
                ? room.getName()
                : room.getName() + " - " + room.getDescription();
            result.append("<option value=\"")
                .append(escapeHtml(room.getName()))
                .append("\">")
                .append(escapeHtml(roomLabel))
                .append("</option>");
        }
        result.append("</select>")
            .append("<button type=\"submit\">Continue</button>")
            .append("</form>");
        return result.toString();
    }

    /**
     * Render a date picker form with browser-native calendar UI.
     */
    private String renderDatePickerForm( final String basePath, final String serviceName, final String roomName, final List<String> dates )
    {
        final String min = dates.get(0);
        final String max = dates.get(dates.size() - 1);

        final StringBuilder result = new StringBuilder();
        result.append("<form class=\"selector-form\" method=\"post\" action=\"")
            .append(escapeHtml(basePath))
            .append("/\">")
            .append("<input type=\"hidden\" name=\"intent\" value=\"date\" />")
            .append("<input type=\"hidden\" name=\"service\" value=\"")
            .append(escapeHtml(serviceName))
            .append("\" />")
            .append("<input type=\"hidden\" name=\"room\" value=\"")
            .append(escapeHtml(roomName))
            .append("\" />")
            .append("<label for=\"date\">Date (UTC)</label>")
            .append("<input id=\"date\" name=\"date\" type=\"date\" min=\"")
            .append(min)
            .append("\" max=\"")
            .append(max)
            .append("\" value=\"")
            .append(max)
            .append("\" required />")
            .append("<button type=\"submit\">Show logs</button>")
            .append("</form>")
            .append("<p class=\"hint\">Available date range: ")
            .append(escapeHtml(min))
            .append(" to ")
            .append(escapeHtml(max))
            .append(" (UTC).</p>");
        return result.toString();
    }

    /**
     * Render a message table for one day of room logs.
     */
    private String renderMessagesTable( final String basePath, final String serviceName, final String roomName,
                                        final String date, final List<LogsBrowserServlet.Message> messages,
                                        final HighlightRange highlightRange )
    {
        if (messages.isEmpty()) {
            return "<p>No messages were found on this date.</p>";
        }

        final String canonicalPath = basePath + "/" + encodePathSegment(serviceName) + "/" + encodePathSegment(roomName) + "/" + date;
        final StringBuilder result = new StringBuilder();
        if (highlightRange.isEnabled()) {
            result.append("<p class=\"hint\">Highlighting message #")
                .append(highlightRange.getStart())
                .append(highlightRange.getStart() == highlightRange.getEnd() ? "" : " to #" + highlightRange.getEnd())
                .append(".</p>");
        }

        result.append("<div id=\"table-wrapper\"><table id=\"message-table\"><thead><tr><th>Time (UTC)</th><th>Nickname</th><th>Message</th></tr></thead><tbody>");
        for (int i = 0; i < messages.size(); i++) {
            final LogsBrowserServlet.Message message = messages.get(i);
            final int id = i + 1;
            final boolean highlighted = highlightRange.includes(id);
            result.append("<tr id=\"m-")
                .append(id)
                .append("\" data-message-id=\"")
                .append(id)
                .append("\" data-canonical-path=\"")
                .append(escapeHtml(canonicalPath))
                .append("\" class=\"message-row ")
                .append(highlighted ? "selected" : "")
                .append("\"><td>")
                .append(escapeHtml(formatUtcTime(message.getTimestamp())))
                .append("</td><td>")
                .append(escapeHtml(message.getNickname()))
                .append("</td><td>")
                .append(escapeHtml(message.getMessage()))
                .append("</td></tr>");
        }
        result.append("</tbody></table></div>");
        return result.toString();
    }

    /**
     * Render quick navigation to the previous/next available day for this room.
     */
    private String renderDayNavigation( final String basePath, final String serviceName, final String roomName,
                                        final String selectedDate, final List<String> dates,
                                        final Locale locale )
    {
        if (dates.isEmpty()) {
            return "";
        }

        final int selectedIndex = dates.indexOf(selectedDate);
        final String previousDate = selectedIndex > 0 ? dates.get(selectedIndex - 1) : null;
        final String nextDate = selectedIndex >= 0 && selectedIndex < dates.size() - 1 ? dates.get(selectedIndex + 1) : null;
        final String selectedDateLabel = formatHumanDate(selectedDate, locale);
        final String previousDateLabel = previousDate == null ? "" : formatHumanDate(previousDate, locale);
        final String nextDateLabel = nextDate == null ? "" : formatHumanDate(nextDate, locale);

        final StringBuilder result = new StringBuilder();
        result.append("<nav class=\"day-nav\" aria-label=\"Day navigation\">");
        result.append(previousDate == null
            ? "<span class=\"day-nav-disabled\">&larr; Previous day</span>"
            : "<a class=\"day-nav-link\" href=\"" + buildDatePath(basePath, serviceName, roomName, previousDate) + "\">&larr; " + escapeHtml(previousDateLabel) + "</a>");
        result.append("<span class=\"day-nav-current\">")
            .append(escapeHtml(selectedDateLabel))
            .append("</span>");
        result.append(nextDate == null
            ? "<span class=\"day-nav-disabled\">Next day &rarr;</span>"
            : "<a class=\"day-nav-link\" href=\"" + buildDatePath(basePath, serviceName, roomName, nextDate) + "\">" + escapeHtml(nextDateLabel) + " &rarr;</a>");
        result.append("</nav>");
        return result.toString();
    }

    /**
     * Convert an ISO date value to a human-readable localized label.
     */
    private String formatHumanDate( final String isoDate, final Locale locale )
    {
        try {
            final LocalDate parsed = LocalDate.parse(isoDate, ISO_LOCAL_DATE);
            return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .format(parsed);
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Build the URL path for one service/room/date combination.
     */
    private String buildDatePath( final String basePath, final String serviceName, final String roomName, final String date )
    {
        return basePath + "/" + encodePathSegment(serviceName) + "/" + encodePathSegment(roomName) + "/" + date;
    }

    /**
     * Parse query parameters that indicate one message ID or an inclusive message range.
     */
    private HighlightRange parseHighlightRange( final HttpServletRequest request )
    {
        final Integer singleId = parsePositiveInteger(request.getParameter("id"));
        if (singleId != null) {
            return new HighlightRange(singleId, singleId);
        }

        final Integer from = parsePositiveInteger(request.getParameter("from"));
        final Integer to = parsePositiveInteger(request.getParameter("to"));
        if (from == null && to == null) {
            return HighlightRange.disabled();
        }

        final int start = from == null ? to : from;
        final int end = to == null ? from : to;
        if (start <= end) {
            return new HighlightRange(start, end);
        }

        return new HighlightRange(end, start);
    }

    /**
     * Parse a positive integer query parameter value.
     */
    private Integer parsePositiveInteger( final String value )
    {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            final int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Escape dynamic values before embedding them into HTML.
     */
    private static String escapeHtml( final String value )
    {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /**
     * Null-safe trim helper for request parameter values.
     */
    private static String safeTrim( final String value )
    {
        return value == null ? "" : value.trim();
    }

    /**
     * Model for one room option in the room selector.
     */
    private static final class RoomOption
    {
        private final String name;
        private final String description;

        /**
         * Construct one room option.
         */
        private RoomOption( final String name, final String description )
        {
            this.name = name == null ? "" : name;
            this.description = description == null ? "" : description;
        }

        /**
         * Return the room name.
         */
        private String getName()
        {
            return name;
        }

        /**
         * Return the room description.
         */
        private String getDescription()
        {
            return description;
        }
    }

    /**
     * Inclusive message sequence range to highlight in the table.
     */
    private static final class HighlightRange
    {
        private final int start;
        private final int end;
        private final boolean enabled;

        /**
         * Construct an enabled inclusive highlight range.
         */
        private HighlightRange( final int start, final int end )
        {
            this.start = start;
            this.end = end;
            this.enabled = true;
        }

        /**
         * Construct a disabled highlight range.
         */
        private HighlightRange()
        {
            this.start = -1;
            this.end = -1;
            this.enabled = false;
        }

        /**
         * Factory for a disabled highlight range.
         */
        private static HighlightRange disabled()
        {
            return new HighlightRange();
        }

        /**
         * Indicates if this range should be applied.
         */
        private boolean isEnabled()
        {
            return enabled;
        }

        /**
         * Check if a message sequence value falls within this range.
         */
        private boolean includes( final int value )
        {
            return enabled && value >= start && value <= end;
        }

        /**
         * Return the inclusive start of the range.
         */
        private int getStart()
        {
            return start;
        }

        /**
         * Return the inclusive end of the range.
         */
        private int getEnd()
        {
            return end;
        }
    }

    public static class Message
    {
        private String timestamp;
        private String nickname;
        private String message;

        public Message() {}

        public String getTimestamp()
        {
            return timestamp;
        }

        public void setTimestamp( final String timestamp )
        {
            this.timestamp = timestamp;
        }

        public String getNickname()
        {
            return nickname;
        }

        public void setNickname( final String nickname )
        {
            this.nickname = nickname;
        }

        public String getMessage()
        {
            return message;
        }

        public void setMessage( final String message )
        {
            this.message = message;
        }
    }
}



