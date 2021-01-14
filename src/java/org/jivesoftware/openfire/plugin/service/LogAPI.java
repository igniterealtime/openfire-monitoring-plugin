package org.jivesoftware.openfire.plugin.service;

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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Path("logs")
@Produces( MediaType.APPLICATION_JSON)
public class LogAPI
{

   public static final SystemProperty<Boolean> PROP_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
       .setKey("archive.settings.logapi.enabled" )
       .setDefaultValue( true )
       .setDynamic( true )
       .setPlugin(MonitoringConstants.PLUGIN_NAME)
       .build();

    private static final Logger Log = LoggerFactory.getLogger(LogAPI.class);

    /**
     * Returns a listing of services that have at least one MUC room in it for which logs are publicly available.
     */
    @GET
    @Path("/")
    public Response getLoggedServiceNames()
    {
        if (!PROP_ENABLED.getValue()) {
            Log.debug( "Denying access to service that's configured by configuration.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        final List<MultiUserChatService> multiUserChatServices = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices();
        final List<String> serviceNames = multiUserChatServices.stream()
            .filter(s -> s.getChatRooms().stream().anyMatch(r -> r.isLogEnabled() && r.isPublicRoom()))
            .map(MultiUserChatService::getServiceName)
            .collect(Collectors.toList() );

        Log.debug( "Responding to API call with {} servicename(s).", serviceNames.size());
        return Response.ok( serviceNames ).build();
    }

    /**
     * Returns a listing of MUC rooms in the specified service that for which logs are publicly available.
     */
    @GET
    @Path("/{serviceName}")
    public Response getLoggedServiceNames( @PathParam("serviceName") String serviceName )
    {
        if (!PROP_ENABLED.getValue()) {
            Log.debug( "Denying access to service that's configured by configuration.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
        if (multiUserChatService == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as no service with name '{}' could be found.", serviceName);
            return Response.noContent().build();
        }

        final List<String> roomNames = multiUserChatService.getChatRooms().stream()
            .filter( r -> r.isLogEnabled() && r.isPublicRoom() )
            .map( MUCRoom::getName )
            .collect(Collectors.toList() );

        Log.debug( "Responding to API call with {} roomname(s) for service: {}", roomNames.size(), serviceName);
        return Response.ok( roomNames ).build();
    }

    /**
     * Returns a listing of dates for which logs are publicly available of the specified MUC room in the specified service
     */
    @GET
    @Path("/{serviceName}/{roomName}")
    public Response getLoggedDates( @PathParam("serviceName") String serviceName, @PathParam("roomName") String roomName  )
    {
        if (!PROP_ENABLED.getValue()) {
            Log.debug( "Denying access to service that's configured by configuration.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
        if (multiUserChatService == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as no service with name '{}' could be found.", serviceName);
            return Response.noContent().build();
        }

        final MUCRoom chatRoom = multiUserChatService.getChatRoom(roomName);
        if ( chatRoom == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as no room with name '{}' in service with name '{}' could be found.", roomName, serviceName);
            return Response.noContent().build();
        }

        // TODO: parse dates that have actual messages, instead of getting the first and last date with messages, and assuming that every date in between has messages.
        final Instant start = MucMamPersistenceManager.getDateOfFirstLog( chatRoom );
        Log.debug( "Timestamp of first logged message in room '{}': {}", roomName, start);

        if ( start == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as messages can be found for room with name '{}' in service with name '{}'.", roomName, serviceName);
            return Response.noContent().build();
        }

        final Instant end = MucMamPersistenceManager.getDateOfLastLog( chatRoom );
        Log.debug( "Timestamp of last logged message in room '{}': {}", roomName, end);

        final List<String> dates = new ArrayList<>();
        Instant needle = start;

        final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone( ZoneOffset.UTC );
        while (!needle.isAfter(end)) {
            dates.add( formatter.format( needle ));
            needle = needle.plus(1, ChronoUnit.DAYS );
        }

        return Response.ok( dates ).build();
    }

    @GET
    @Path("/{serviceName}/{roomName}/{date}")
    public Response getMessages( @PathParam("serviceName") String serviceName, @PathParam("roomName") String roomName, @PathParam("date") String date )
    {
        if (!PROP_ENABLED.getValue()) {
            Log.debug( "Denying access to service that's configured by configuration.");
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
        if (multiUserChatService == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as no service with name '{}' could be found.", serviceName);
            return Response.noContent().build();
        }

        final MUCRoom chatRoom = multiUserChatService.getChatRoom(roomName);
        if ( chatRoom == null ) {
            Log.debug( "Responding to API call with 204 'No Content' as no room with name '{}' in service with name '{}' could be found.", roomName, serviceName);
            return Response.noContent().build();
        }

        final Instant onOrAfter;
        try
        {
            onOrAfter = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch ( DateTimeParseException ex ) {
            Log.warn( "Unable to parse as 'yyyy-MM-dd' date: {}", date, ex);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        final Instant before = onOrAfter.plus(1, ChronoUnit.DAYS);
        final List<Message> messages = getMessages( onOrAfter, before, chatRoom );
        return Response.ok( messages ).build();
    }

    public static class Message {
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

    public static List<Message> getMessages( Instant after, Instant before, MUCRoom room )
    {
        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<Message> msgs = new LinkedList<>();
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

                final Message message = new Message();
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

}
