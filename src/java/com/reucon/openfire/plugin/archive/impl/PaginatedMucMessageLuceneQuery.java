package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PaginatedMucMessageLuceneQuery
{
    private static final Logger Log = LoggerFactory.getLogger( PaginatedMucMessageLuceneQuery.class );

    private static SystemProperty<Boolean> IGNORE_RETRIEVAL_EXCEPTIONS = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("archive.ignore-retrieval-exceptions")
        .setDefaultValue(false)
        .setDynamic(true)
        .setPlugin(MonitoringConstants.PLUGIN_NAME)
        .build();

    private final Date startDate;
    private final Date endDate;
    private final MUCRoom owner;
    private final JID messageOwner;
    private final JID with;
    private final String query;

    public PaginatedMucMessageLuceneQuery(final Date startDate, final Date endDate, final MUCRoom owner, final JID messageOwner, final JID with, final String query )
    {
        this.startDate = startDate == null ? new Date( 0L ) : startDate ;
        this.endDate = endDate == null ? new Date() : endDate;
        this.owner = owner;
        this.messageOwner = messageOwner;
        this.with = with;
        this.query = query;
    }

    protected IndexSearcher getSearcher() throws IOException
    {
        final MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(MonitoringConstants.NAME);
        final MessageIndexer archiveIndexer = plugin.getMessageIndexer();
        final IndexSearcher searcher = archiveIndexer.getSearcher();
        return searcher;
    }


    public List<ArchivedMessage> getPage( final Long after, final Long before, final int maxResults, final boolean isPagingBackwards ) throws DataRetrievalException {
        Log.debug( "Retrieving archived messages page. After: {}, Before: {}, maxResults: {}, isPagingBackwards: {}", after, before, maxResults, isPagingBackwards);
        final List<ArchivedMessage> result = new ArrayList<>();
        try
        {
            final IndexSearcher searcher = getSearcher();
            final Query query = getLuceneQueryForPage(after, before);
            Log.debug("Executing query: {}", query);

            final TopFieldDocs indexResult = searcher.search(query, maxResults, getSort(isPagingBackwards));
            Log.debug("Index result: {}", indexResult);

            for ( final ScoreDoc scoreDoc : indexResult.scoreDocs )
            {
                Log.debug("Iterating over doc: {}", scoreDoc);
                final Document doc = searcher.doc(scoreDoc.doc);
                final long messageID = Long.parseLong(doc.get("messageID"));
                Log.debug("message ID: {}", messageID);
                final ArchivedMessage archivedMessage = MucMamPersistenceManager.getArchivedMessage(messageID, owner);
                if ( archivedMessage != null ) {
                    result.add( archivedMessage );
                }

                Log.debug("Got message? {}", archivedMessage != null);
            }

            // The order of items in the page must always be chronologically, oldest to newest, even when paging backwards.
            if ( isPagingBackwards ) {
                Collections.reverse( result );
            }
        }
        catch ( Exception e ) {
            Log.warn( "An exception occurred while trying to query the Lucene index to get messages from archive of room {}.", owner.getJID(), e );
            if (!IGNORE_RETRIEVAL_EXCEPTIONS.getValue()) {
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
            Log.warn( "An exception occurred while trying to get a count of messages that match a query for message from archive of room {}.", owner.getJID(), e );
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

        // To retrieve all messages for a 'MUC archive', combine the following:
        // - look up messages from the archive of the MUC that are not private
        // - look up messages from the archive of the MUC that are private, where user making the request is a sender or recipient.
        final BooleanQuery ownerFilter = new BooleanQuery.Builder()
            .add(new TermQuery(new Term("room", owner.getJID().toBareJID() ) ), BooleanClause.Occur.MUST ) // room

            .setMinimumNumberShouldMatch(1)
            // Either non-private messages...
            .add(new TermQuery( new Term( "isPrivateMessage", "false") ), BooleanClause.Occur.SHOULD )

            // ... or private, sent or received by the message owner
            .add(new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1) // One of the 'SHOULD' terms (pmFromJID or pmToJID) must be true (isPrivateMessage is a 'MUST' and ignored by this).
                .add( new TermQuery( new Term( "isPrivateMessage", "true") ), BooleanClause.Occur.MUST )
                .add( new TermQuery(new Term("pmFromJID", messageOwner.toBareJID() ) ), BooleanClause.Occur.SHOULD )
                .add( new TermQuery(new Term("pmToJID", messageOwner.toBareJID() ) ), BooleanClause.Occur.SHOULD )
                .build(), BooleanClause.Occur.SHOULD )
            .build();

        builder.add(ownerFilter, BooleanClause.Occur.MUST);

        // Limit potential results to the requested time range. Note that these values are always non-null in this method (might be 'EPOCH' though).
        final Query dateRangeQuery = NumericDocValuesField.newSlowRangeQuery("sentDate", startDate.getTime(), endDate.getTime());
        builder.add(dateRangeQuery, BooleanClause.Occur.MUST);

        // If defined, limit to specific senders.
        if ( with != null ) {
            // Always limit to the bare JID of the sender.
            builder.add(new TermQuery(new Term("pmToJID", with.toBareJID() ) ), BooleanClause.Occur.MUST );

            // For MUC PMs, if the query specified a more specific full JID, the resource part is ignored. It is unlikely that the sender
            // of the PM was aware of the recipient's resource that the message was delivered to.
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
        return new Sort(new SortField("sentDate", SortField.Type.LONG, isPagingBackwards));
    }

    @Override
    public String toString()
    {
        return "PaginatedMessageLuceneQuery{" +
            "startDate=" + startDate +
            ", endDate=" + endDate +
            ", room=" + owner.getJID() +
            ", with=" + with +
            ", query='" + query + '\'' +
            '}';
    }
}
