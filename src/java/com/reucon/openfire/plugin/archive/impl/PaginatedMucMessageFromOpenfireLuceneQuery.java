package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.xep0313.IQQueryHandler;
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
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

public class PaginatedMucMessageFromOpenfireLuceneQuery extends AbstractPaginatedMamMucQuery
{
    private static final Logger Log = LoggerFactory.getLogger(PaginatedMucMessageFromOpenfireLuceneQuery.class);

    /**
     * Creates a query for messages from a message archive of a multi-user chat room.
     *
     * Two identifying JIDs are provided to this method: one is the JID of the room that's being queried (the 'archive
     * owner'). To be able to return the private messages for a particular user from the room archive, an additional JID
     * is provided, that identifies the user for which to retrieve the messages.
     *
     * @param startDate Start (inclusive) of period for which to return messages. EPOCH will be used if no value is provided.
     * @param endDate End (inclusive) of period for which to return messages. 'now' will be used if no value is provided.
     * @param room The message archive owner (the chat room).
     * @param sender The entity for which to return messages (typically the JID of the entity making the request).
     * @param query A search string to be used for text-based search.
     */
    public PaginatedMucMessageFromOpenfireLuceneQuery(@Nullable final Date startDate, @Nullable final Date endDate, @Nonnull final MUCRoom room, @Nullable final JID sender, @Nonnull final String query)
    {
        super(startDate, endDate, room, sender, null, query);
    }

    protected IndexSearcher getSearcher() throws IOException
    {
        final Optional<Plugin> plugin = XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME);
        if (!plugin.isPresent()) {
            throw new IllegalStateException("Unable to obtain Lucene Index Searcher! The Monitoring plugin does not appear to be loaded on this machine.");
        }
        final MucIndexer mucIndexer = ((MonitoringPlugin)plugin.get()).getMucIndexer();
        final IndexSearcher searcher = mucIndexer.getSearcher();
        return searcher;
    }

    @Override
    public List<ArchivedMessage> getPage(final Long after, final Long before, final int maxResults, final boolean isPagingBackwards) throws DataRetrievalException {
        Log.debug( "Retrieving archived messages page. After: {}, Before: {}, maxResults: {}, isPagingBackwards: {}", after, before, maxResults, isPagingBackwards);
        final List<ArchivedMessage> result = new ArrayList<>();
        try
        {
            final IndexSearcher searcher = getSearcher();
            final Query query = getLuceneQueryForPage(after, before);
            final TopFieldDocs indexResult = searcher.search(query, maxResults, getSort(isPagingBackwards));

            for ( final ScoreDoc scoreDoc : indexResult.scoreDocs )
            {
                final Document doc = searcher.doc(scoreDoc.doc);
                final long messageID = Long.parseLong(doc.get("messageID"));
                final ArchivedMessage archivedMessage = MucMamPersistenceManager.getArchivedMessage(messageID, room);
                if ( archivedMessage != null ) {
                    result.add( archivedMessage );
                }
            }

            // The order of items in the page must always be chronologically, oldest to newest, even when paging backwards.
            if ( isPagingBackwards ) {
                Collections.reverse( result );
            }
        }
        catch ( Exception e ) {
            Log.warn( "An exception occurred while trying to query the Lucene index to get messages from room {}.", room, e );
            if (!IQQueryHandler.IGNORE_RETRIEVAL_EXCEPTIONS.getValue()) {
                throw new DataRetrievalException(e);
            }
        }
        Log.debug( "Returning {} result(s).", result.size() );
        return result;
    }

    /**
     * Returns the amount of messages that are in the entire, unlimited/unpaged, result set.
     *
     * @return A message count, or -1 if unavailable.
     */
    @Override
    public int getTotalCount() {
        try
        {
            final Query query = getLuceneQueryForAllResults();
            final IndexSearcher searcher = getSearcher();
            final TotalHitCountCollector collector = new TotalHitCountCollector();
            searcher.search( query, collector );
            final int result = collector.getTotalHits();
            Log.debug( "Total number for unpaged query is: {}. Query: {}", result, query );

            return result;
        }
        catch ( Exception e )
        {
            Log.warn( "An exception occurred while trying to get a count of messages that match a query for message from room {}.", room, e );
            return -1;
        }
    }

    protected Query getLuceneQueryForAllResults() throws ParseException
    {
        final StandardAnalyzer analyzer = new StandardAnalyzer();

        final BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Create the query based on the search terms.
        final Query textQuery = new QueryParser("body", analyzer).parse( QueryParser.escape(query) );
        builder.add(textQuery, BooleanClause.Occur.MUST );

        // Limit to the chat room.
        builder.add(LongPoint.newExactQuery("roomID", room.getID()), BooleanClause.Occur.MUST);

        // Limit potential results to the requested time range. Note that these values are always non-null in this method (might be 'EPOCH' though).
        final Query dateRangeQuery = NumericDocValuesField.newSlowRangeQuery("logTime", startDate.getTime(), endDate.getTime());
        builder.add(dateRangeQuery, BooleanClause.Occur.MUST);

        // If defined, limit to specific senders.
        if ( messageOwner != null ) {
            // Always limit to the bare JID of the sender.
            builder.add(new TermQuery(new Term("senderBare", messageOwner.toBareJID() ) ), BooleanClause.Occur.MUST );

            // If the query specified a more specific full JID, include the resource part in the filter too.
            if ( messageOwner.getResource() != null ) {
                builder.add(new TermQuery( new Term( "senderResource", messageOwner.getResource() ) ), BooleanClause.Occur.MUST );
            }
        }

        final BooleanQuery query = builder.build();
        Log.debug( "Constructed all-result query: {}", query);
        return query;
    }

    protected Query getLuceneQueryForPage( final Long after, final Long before ) throws ParseException
    {
        final BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(getLuceneQueryForAllResults(), BooleanClause.Occur.MUST );

        // Limit by 'before' and 'after', if applicable.
        if ( after != null || before != null) {
            builder.add(NumericDocValuesField.newSlowRangeQuery( "messageIDRange",
                                                 after != null ? Math.addExact(after, 1) : Long.MIN_VALUE,
                                                 before != null ? Math.addExact(before, -1) : Long.MAX_VALUE
            ), BooleanClause.Occur.MUST );
        }

        final BooleanQuery query = builder.build();
        Log.debug( "Constructed page-result query: {}", query);
        return query;
    }

    public Sort getSort( final boolean isPagingBackwards ) {
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
            ", sender=" + messageOwner +
            ", query='" + query + '\'' +
            '}';
    }
}
