package com.reucon.openfire.plugin.archive.model;

import java.util.Date;

public class MamArchiveMetadata {
    private long startId;
    private Date startTimestamp;
    private long endId;
    private Date endTimestamp;
    private boolean isEmpty;
    
    // Constructor for empty archive
    public MamArchiveMetadata() {
        this.isEmpty = true;
    }
    
    // Constructor with start and end data
    public MamArchiveMetadata(long startId, Date startTimestamp, long endId, Date endTimestamp) {
        this.startId = startId;
        this.startTimestamp = startTimestamp;
        this.endId = endId;
        this.endTimestamp = endTimestamp;
        this.isEmpty = false;
    }
    
    public boolean isEmpty() {
        return isEmpty;
    }

    public String getStartId() {
        return String.valueOf(startId);
    }

    public String getEndId() {
        return String.valueOf(endId);
    }

    public Date getStartTimestamp() {
        return startTimestamp;
    }

    public Date getEndTimestamp() {
        return endTimestamp;
    }
}
