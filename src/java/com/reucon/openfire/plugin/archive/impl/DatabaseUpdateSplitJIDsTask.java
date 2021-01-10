package com.reucon.openfire.plugin.archive.impl;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Database update script 1 of the monitoring plugin introduces new columns to the ofMessageArchivePlugin. These new
 * column are intended to hold the optional resource-part of a JID, while the original column, that previously held
 * the full JID, now intends to hold the bare JID.
 *
 * The update script does not perform migration of existing data. This migration is performed by this method.
 *
 * The functionality in this method is idempotent. However, as execution can be costly, especially on larger data
 * sets, even when there's no work to perform, a property is set after the first successful execution. This property
 * prevents subsequent executions.
 *
 * Additionally, this method will skip execution when the code is running in a cluster and the local node is not the
 * senior node. This intends to prevent multiple nodes from attempting to make the same database changes
 * simultaneously.
 *
 * This task is implemented in the form of a Runnable, in order to allow it to be executed in a background thread, that
 * can be interrupted upon loading/unloading a plugin.
 *
 * @see <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/113">Issue #113: Improve usage of 'resource' columns in the database</a>
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class DatabaseUpdateSplitJIDsTask implements Runnable
{
    private static Logger Log = LoggerFactory.getLogger(DatabaseUpdateSplitJIDsTask.class );
    private static SystemProperty<Boolean> JID_COLUMNS_HAVE_BEEN_MIGRATED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("conversation.database.jid-columns-have-been-migrated")
        .setDefaultValue(false)
        .setDynamic(true)
        .setPlugin("monitoring")
        .build();

    @Override
    public void run()
    {
        if ( JID_COLUMNS_HAVE_BEEN_MIGRATED.getValue() )
        {
            Log.debug( "No need to run database table migration to split full JID values into bare JID and resource-part components: configuration indicates that migration already occurred." );
            return;
        }

        if ( ClusterManager.isClusteringEnabled() && !ClusterManager.isSeniorClusterMember() )
        {
            Log.debug( "Skipping database table migration to split full JID values into bare JID and resource-part components, as we're not the senior cluster member." );
            return;
        }

        Log.info( "Running database table migration to split full JID values into bare JID and resource-part components." );
        Connection connection = null;
        PreparedStatement pstmtFind = null;
        PreparedStatement pstmtUpdate = null;
        ResultSet rs = null;
        try
        {
            // Preventing the driver to collect all results at once depends on auto-commit from being disabled, at
            // least for postgres. Getting a 'transaction' connection will ensure this (if supported).
            connection = DbConnectionManager.getTransactionConnection();

            // The same migration needs to happen for several columns. To prevent code duplication, an iteration is used.
            final String[] applicableColumnNames = new String[] { "fromJID", "toJID" };
            for ( final String applicableColumnName : applicableColumnNames )
            {
                Log.debug( "Find columns with full JIDs in the {} column: these are rows that have NULL in the resource column, and a slash in the JID column.", applicableColumnName );
                final String findQuery = "SELECT DISTINCT "+applicableColumnName+" FROM ofMessageArchive WHERE "+applicableColumnName+"Resource IS NULL AND "+applicableColumnName+" LIKE '%/%'";
                final String updateQuery = "UPDATE ofMessageArchive SET "+applicableColumnName+" = ?, "+applicableColumnName+"Resource = ? WHERE "+applicableColumnName+" = ? AND "+applicableColumnName+"Resource IS NULL";
                pstmtFind = connection.prepareStatement(findQuery);
                pstmtFind.setFetchSize(250);

                rs = pstmtFind.executeQuery();

                Log.debug( "Updating rows to split the full JID (in column {}) into a bare JID and resource-part...", applicableColumnName );
                long progress = 0;
                Instant lastProgressReport = Instant.now();
                final Set<JID> toTransform = new HashSet<>();
                while ( rs.next() )
                {
                    final String originalValue = rs.getString(1);
                    final JID jid = new JID(originalValue);
                    if ( jid.getResource() != null )
                    {
                        toTransform.add(jid);
                    }
                }

                Log.debug( "Identified {} JIDs (in column {}) to be migrated...", toTransform.size(), applicableColumnName );
                rs.close();
                pstmtFind.close();

                pstmtUpdate = connection.prepareStatement(updateQuery);
                for ( final JID jid : toTransform )
                {
                    pstmtUpdate.setString(1, jid.toBareJID());
                    pstmtUpdate.setString(2, jid.getResource());
                    pstmtUpdate.setString(3, jid.toString());
                    pstmtUpdate.execute();

                    // When there are _many_ messages to be processed, log an occasional progress indicator, to let admins know that things are still churning.
                    ++progress;
                    if ( lastProgressReport.isBefore(Instant.now().minus(10, ChronoUnit.SECONDS)) )
                    {
                        Log.debug("... processed {} out of {} JIDs so far.", progress, toTransform.size());
                        lastProgressReport = Instant.now();
                    }
                }
                Log.debug( "Finished migrating all JIDs for the {} column. Processed {} JIDs in total.", applicableColumnName, progress );
                pstmtUpdate.close();
            }

            Log.info( "Successfully finished running a database table migration to split full JID values into bare JID and resource-part components." );
            JID_COLUMNS_HAVE_BEEN_MIGRATED.setValue(true);
        }
        catch ( Exception e )
        {
            Log.error( "An unexpected exception occurred while performing a database table migration to split full JID values into bare JID and resource-part components. The database of the Monitoring plugin might be in an inconsistent state. Consider reverting to a previous backup.", e );
        }
        finally
        {
            DbConnectionManager.closeResultSet( rs );
            DbConnectionManager.closeStatement( pstmtFind );
            DbConnectionManager.closeStatement( pstmtUpdate );
            DbConnectionManager.closeTransactionConnection( connection, false );
        }
    }
}
