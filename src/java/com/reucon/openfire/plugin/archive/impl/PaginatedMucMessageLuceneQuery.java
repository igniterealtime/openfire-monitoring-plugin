package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PaginatedMucMessageLuceneQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMucMessageLuceneQuery.class );

    private final Date startDate;
    private final Date endDate;
    private final MUCRoom room;
    private final JID sender;
    private final String query;
    private final Long after;
    private final Long before;
    private final int maxResults;
    private final boolean isPagingBackwards;

    private long totalCountOfLastQuery = -1;

    public PaginatedMucMessageLuceneQuery( final Date startDate, final Date endDate, final MUCRoom room, final JID sender, final String query, final Long after, final Long before, final int maxResults, final boolean isPagingBackwards )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.room = room;
        this.sender = sender;
        this.query = query;
        this.after = after;
        this.before = before;
        this.maxResults = maxResults;
        this.isPagingBackwards = isPagingBackwards;
    }

    public List<ArchivedMessage> getArchivedMessages() {
        Log.debug( "... get archived messages");
        final List<ArchivedMessage> result = new ArrayList<>();
        try
        {
            final MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(MonitoringConstants.NAME);
            final MucIndexer mucIndexer = (MucIndexer) plugin.getModule(MucIndexer.class);
            final IndexSearcher searcher = mucIndexer.getSearcher();

            final TopFieldDocs indexResult = searcher.search(getLuceneQuery(), maxResults, getSort());

            for ( final ScoreDoc scoreDoc : indexResult.scoreDocs )
            {
                final Document doc = searcher.doc(scoreDoc.doc);
                final long messageID = Long.parseLong(doc.get("messageID"));
                final ArchivedMessage archivedMessage = MucMamPersistenceManager.getArchivedMessage(messageID, room);
                if ( archivedMessage != null ) {
                    result.add( archivedMessage );
                }
            }

            // Register the total count for this query, to prevent having to query that independently.
            totalCountOfLastQuery = indexResult.totalHits.value;
        }
        catch ( Exception e ) {
            Log.warn( "An exception occurred while trying to query the Lucene index to get messages from room {}.", room, e );
        }
        return result;
    }

    public int getTotalCountOfLastQuery()
    {
        return (int) totalCountOfLastQuery;
    }

    protected Query getLuceneQuery() throws ParseException
    {
        final StandardAnalyzer analyzer = new StandardAnalyzer();

        final BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Create the query based on the search terms.
        final Query textQuery = new QueryParser("body", analyzer).parse(query);
        builder.add(textQuery, BooleanClause.Occur.MUST );

        // Limit to the chat room.
        builder.add(LongPoint.newExactQuery("roomID", room.getID()), BooleanClause.Occur.MUST);

        // Limit potential results to the requested time range. Note that these values are always non-null in this method (might be 'EPOCH' though).
        final Query dateRangeQuery = NumericDocValuesField.newSlowRangeQuery("logTime", startDate.getTime(), endDate.getTime());
        builder.add(dateRangeQuery, BooleanClause.Occur.MUST);

        // If defined, limit to specific senders.
        if ( sender != null ) {
            // Always limit to the bare JID of the sender.
            builder.add(new TermQuery(new Term("senderBare", sender.toBareJID() ) ), BooleanClause.Occur.MUST );

            // If the query specified a more specific full JID, include the resource part in the filter too.
            if ( sender.getResource() != null ) {
                builder.add(new TermQuery( new Term( "senderResource", sender.getResource() ) ), BooleanClause.Occur.MUST );
            }
        }

        // Limit by 'before' and 'after', if applicable.
        if ( after != null || before != null) {
            builder.add(LongPoint.newRangeQuery( "messageID",
                                                 after != null ? after : Long.MIN_VALUE,
                                                 before != null ? before : Long.MAX_VALUE
            ), BooleanClause.Occur.MUST );
        }

        final BooleanQuery query = builder.build();
        Log.debug( "Constructed query: {}", query);
        return query;
    }

    public Sort getSort() {
        // Always sort based on date.
        return new Sort(new SortField("logTime", SortField.Type.LONG, isPagingBackwards));
    }

    @Override
    public String toString()
    {
        return "PaginatedMucMessageLuceneQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", room=" + room +
            ", sender=" + sender +
            ", query='" + query + '\'' +
            ", after=" + after +
            ", before=" + before +
            ", maxResults=" + maxResults +
            ", isPagingBackwards=" + isPagingBackwards +
            ", totalCountOfLastQuery=" + totalCountOfLastQuery +
            '}';
    }
}
