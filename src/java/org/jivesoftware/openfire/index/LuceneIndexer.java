package org.jivesoftware.openfire.index;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dom4j.DocumentFactory;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jivesoftware.openfire.archive.ArchiveIndexer;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMLProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class LuceneIndexer
{
    protected final Logger Log;

    protected TaskEngine taskEngine;
    protected RebuildFuture rebuildFuture;
    private File searchDir;
    private XMLProperties indexProperties;
    private Directory directory;
    private IndexSearcher searcher;
    private boolean stopped = false;
    private boolean rebuildInProgress = false;
    private TimerTask indexUpdater;

    public LuceneIndexer(TaskEngine taskEngine, File searchDir, String logName)
    {
        this.taskEngine = taskEngine;
        this.searchDir = searchDir;
        Log = LoggerFactory.getLogger(ArchiveIndexer.class.getName() + "["+logName+"]");
    }

    public void start()
    {
        Log.debug("Starting...");
        if ( !searchDir.exists() )
        {
            if ( !searchDir.mkdirs() )
            {
                Log.warn("Lucene index directory '{}' does not exist, but cannot be created!", searchDir);
            }
        }

        boolean indexCreated = false;
        try
        {
            indexProperties = loadPropertiesFile(searchDir);
            directory = FSDirectory.open(searchDir.toPath());
            if ( !DirectoryReader.indexExists(directory) )
            {
                // Create a new index.
                indexCreated = true;
            }
            else
            {
                // See if we can read the format.
                try
                {
                    // TODO make this optional through configuration.
                    Log.debug("Checking Lucene index...");
                    boolean isClean;
                    try ( final CheckIndex check = new CheckIndex(directory) )
                    {
                        check.setChecksumsOnly(true);
                        check.setDoSlowChecks(false);
                        check.setFailFast(true);
                        isClean = check.checkIndex().clean;
                        Log.info("Lucene index {} clean.", isClean ? "is" : "is not");
                    }
                    if ( !isClean )
                    {
                        Log.info("Lucene index is not clean. Removing and rebuilding: {}", isClean);
                        directory.close();
                        FileUtils.deleteDirectory(searchDir);
                        if ( !searchDir.mkdirs() )
                        {
                            Log.warn("Lucene index directory '{}' cannot be recreated!", searchDir);
                        }
                        directory = FSDirectory.open(searchDir.toPath());
                        indexCreated = true;
                    }
                }
                catch ( IndexFormatTooOldException ex )
                {
                    Log.info("Format of Lucene index is to old. Removing and rebuilding.", ex);
                    directory.close();
                    FileUtils.deleteDirectory(searchDir);
                    if ( !searchDir.mkdirs() )
                    {
                        Log.warn("Lucene index directory '{}' cannot be recreated!", searchDir);
                    }
                    directory = FSDirectory.open(searchDir.toPath());
                    indexCreated = true;
                }
            }
        }
        catch ( IOException ioe )
        {
            Log.error("An exception occurred while initializing the Lucene index that is expected to exist in: {}", searchDir, ioe);
        }

        // If the index has never been updated, build it from scratch.
        if ( getLastModified().equals( Instant.EPOCH ) || indexCreated )
        {
            taskEngine.submit(this::rebuildIndex);
        }

        indexUpdater = new TimerTask()
        {
            @Override
            public void run()
            {
                updateIndex();
            }
        };
        final int updateInterval = JiveGlobals.getIntProperty("conversation.search.updateInterval", 5);
        taskEngine.schedule(indexUpdater, JiveConstants.MINUTE * 1, JiveConstants.MINUTE * updateInterval);
    }

    protected synchronized Instant getLastModified()
    {
        String modified = indexProperties.getProperty("lastModified");
        Log.debug("Last modification date: {}", modified);
        if ( modified != null )
        {
            try
            {
                return Instant.ofEpochMilli(Long.parseLong(modified));
            }
            catch ( NumberFormatException nfe )
            {
                Log.warn("Unable to parse 'last modification date' as number: {}", modified);
            }
        }
        Log.debug("Unable to parse 'last modification date' value. Returning EPOCH.");
        return Instant.EPOCH;
    }

    protected synchronized void setLastModified(Instant instant) {
        Log.debug("Updating modification date to: {}", instant);
        indexProperties.setProperty("lastModified", Long.toString(instant.toEpochMilli()));
    }

    public void stop()
    {
        Log.debug("Stopping...");
        stopped = true;
        indexUpdater.cancel();
        if ( searcher != null )
        {
            try
            {
                searcher.getIndexReader().close();
            }
            catch ( Exception e )
            {
                Log.warn("An exception occurred while trying to close the writer of the Lucene search index reader.", e);
            }
            searcher = null;
        }
        try
        {
            directory.close();
        }
        catch ( Exception e )
        {
            Log.error(e.getMessage(), e);
        }
        directory = null;
        indexProperties = null;
        searchDir = null;
        rebuildFuture = null;
    }

    /**
     * Returns the total size of the search index (in bytes).
     *
     * @return the total size of the search index (in bytes).
     */
    public long getIndexSize()
    {
        File[] files = searchDir.listFiles(( dir, name ) -> {
            // Ignore the index properties file since it's not part of the index.
            return !name.equals("indexprops.xml");
        });
        if ( files == null )
        {
            // Search folder does not exist so size of index is 0
            return 0;
        }
        long size = 0;
        for ( File file : files )
        {
            size += file.length();
        }
        return size;
    }

    /**
     * Updates the search index with all new data since the last index update.
     */
    public void updateIndex()
    {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return;
        }

        // If we're currently rebuilding the index, return.
        if (rebuildInProgress) {
            Log.debug("Not updating Lucene index, as a rebuild is in progress.");
            return;
        }

        Log.debug("Updating the Lucene index...");
        final Instant start = Instant.now();

        final Analyzer analyzer = new StandardAnalyzer();
        final IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        try ( final IndexWriter writer = new IndexWriter(directory, iwc) )
        {
            final Instant since = getLastModified();
            final Instant lastModified = doUpdateIndex( writer, since );
            setLastModified(lastModified);

            final Duration duration = Duration.between( start, Instant.now() );
            Log.debug("Finished updating the Lucene index. Duration: {}. Last message timestamp was: {}, now is: {}", duration, since, lastModified);
        }
        catch (IOException ioe) {
            Log.error("An exception occurred while updating the Lucene index.", ioe);
        }
    }

    /**
     * Rebuilds the search index with all archived conversation data. This method returns
     * a Future that represents the status of the index rebuild process (also available
     * via {@link #getIndexRebuildProgress()}). The integer value
     * (values 0 through 100) represents the percentage of work done. If message archiving
     * is disabled, this method will return <tt>null</tt>.
     *
     * @return a Future to indicate the status of rebuilding the index or <tt>null</tt> if
     *      rebuilding the index is not possible.
     */
    public synchronized Future<Integer> rebuildIndex() {
        // Immediately return if the service has been stopped.
        if (stopped) {
            return null;
        }

        // If a rebuild is already happening, return.
        if (rebuildInProgress) {
            return null;
        }

        rebuildInProgress = true;

        // Create a future to track the index rebuild progress.
        rebuildFuture = new RebuildFuture();

        // Create a runnable that will perform the actual rebuild work.
        Runnable rebuildTask = () -> {
            Log.debug("Rebuilding the Lucene index...");
            final Instant start = Instant.now();

            Log.debug("Removing old data from directory: {}", searchDir);
            try {
                FileUtils.cleanDirectory(searchDir);
            } catch ( IOException e ) {
                Log.warn("An exception occured while trying to clean directory '{}' as part of a rebuild of the Lucene index that's in it.", searchDir);
            }

            final Analyzer analyzer = new StandardAnalyzer();
            final IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE); // force re-create (as opposed to CREATE_OR_APPEND)

            try ( final IndexWriter writer = new IndexWriter(directory, iwc) )
            {
                final Instant newest = doRebuildIndex(writer);
                setLastModified(newest);

                final Duration duration = Duration.between(start, Instant.now());
                Log.debug("Finished rebuilding the Lucene index. Duration: {}", duration);

                // Release searcher for it to be re-initialized upon next use (without this, a reference to the old index
                // will prevent it from being removed, resulting in duplicates and increased disk usage).
                if ( searcher != null ) {
                    searcher.getIndexReader().close();
                    searcher = null;
                }
            }
            catch (Exception ioe) {
                Log.error("An exception occurred while rebuilding the Lucene index.", ioe);
            } finally {
                // Done rebuilding the index, so reset state.
                rebuildFuture = null;
                rebuildInProgress = false;
            }
        };
        taskEngine.submit(rebuildTask);

        return rebuildFuture;
    }

    /**
     * Updates the index with all new conversation data since the last index update.
     *
     * @param writer The instance used to modify the index data (cannot be null).
     * @param lastModified The date up until the index has been updated (cannot be null)
     * @return the date of the up until the index has been updated after processing (never null).
     */
    protected abstract Instant doUpdateIndex( final IndexWriter writer, Instant lastModified ) throws IOException;

    /**
     * Updates the index with all conversations that are available. This effectively rebuilds the index.
     *
     * @param writer The instance used to modify the index data (cannot be null).
     * @return the date of the up until the index has been updated after processing (never null).
     */
    public abstract Instant doRebuildIndex( final IndexWriter writer ) throws IOException;

    /**
     * Returns a Future representing the status of an index rebuild operation. This is the
     * same Future returned by the {@link #rebuildIndex()} method; access is provided via
     * this method as a convenience. If the index is not currently being rebuilt, this method
     * will return <tt>null</tt>.
     *
     * @return a Future that represents the index rebuild status or <tt>null</tt> if the
     * index is not being rebuilt.
     */
    public Future<Integer> getIndexRebuildProgress()
    {
        return rebuildFuture;
    }

    /**
     * Returns an IndexSearcher to search the archive index.
     *
     * @return an IndexSearcher.
     * @throws IOException if an IOException occurs.
     */
    public synchronized IndexSearcher getSearcher() throws IOException
    {
        // If the searcher hasn't been instantiated, create it.
        if ( searcher == null )
        {
            DirectoryReader reader = DirectoryReader.open(directory);
            searcher = new IndexSearcher(reader);
        }
        // See if the searcher needs to be closed due to the index being updated.
        final DirectoryReader replacement = DirectoryReader.openIfChanged((DirectoryReader) searcher.getIndexReader());
        if ( replacement != null )
        {
            Log.debug("Returning new Index Searcher (as index was updated)");
            searcher.getIndexReader().close();
            searcher = new IndexSearcher(replacement);
        }
        return searcher;
    }

    /**
     * Loads a property manager for search properties if it isn't already
     * loaded. If an XML file for the search properties isn't already
     * created, it will attempt to make a file with default values.
     */
    private XMLProperties loadPropertiesFile( File searchDir ) throws IOException
    {
        File indexPropertiesFile = new File(searchDir, "indexprops.xml");

        // Make sure the file actually exists. If it doesn't, a new file
        // will be created.
        // If it doesn't exists we have to create it.
        if ( !indexPropertiesFile.exists() )
        {
            org.dom4j.Document doc = DocumentFactory.getInstance().createDocument(
                DocumentFactory.getInstance().createElement("search"));
            // Now, write out to the file.
            try ( Writer out = new FileWriter(indexPropertiesFile) )
            {
                // Use JDOM's XMLOutputter to do the writing and formatting.
                XMLWriter outputter = new XMLWriter(out, OutputFormat.createPrettyPrint());
                outputter.write(doc);
                outputter.flush();
            }
            catch ( Exception e )
            {
                Log.error("An exception occurred while trying to load the Lucene property file that's expected to exist in: {}", searchDir, e);
            }
            // Ignore.
        }
        return new XMLProperties(indexPropertiesFile);
    }

    /**
     * A Future class to track the status of index rebuilding.
     */
    protected static class RebuildFuture implements Future<Integer>
    {

        private int percentageDone = 0;

        public boolean cancel( boolean mayInterruptIfRunning )
        {
            // Don't allow cancels.
            return false;
        }

        public boolean isCancelled()
        {
            return false;
        }

        public boolean isDone()
        {
            return percentageDone == 100;
        }

        public Integer get() throws InterruptedException, ExecutionException
        {
            return percentageDone;
        }

        public Integer get( long timeout, TimeUnit unit ) throws InterruptedException,
            ExecutionException, TimeoutException
        {
            return percentageDone;
        }

        /**
         * Sets the percentage done.
         *
         * @param percentageDone the percentage done.
         */
        public void setPercentageDone( int percentageDone )
        {
            if ( percentageDone < 0 || percentageDone > 100 )
            {
                throw new IllegalArgumentException("Invalid value: " + percentageDone);
            }
            this.percentageDone = percentageDone;
        }
    }
}
