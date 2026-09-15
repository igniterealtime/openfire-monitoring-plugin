package org.jivesoftware.smackx.mam.extended;

/**
 * A {@code <start/>} or {@code <end/>} boundary of a XEP-0313 §5 "Archive metadata" {@code <metadata/>} element:
 * the id and XEP-0082 timestamp of the first, respectively last, message in an archive.
 */
public final class MamMetadataBoundary
{
    private final String id;
    private final String timestamp;

    MamMetadataBoundary(final String id, final String timestamp)
    {
        this.id = id;
        this.timestamp = timestamp;
    }

    public String getId()
    {
        return id;
    }

    public String getTimestamp()
    {
        return timestamp;
    }
}
