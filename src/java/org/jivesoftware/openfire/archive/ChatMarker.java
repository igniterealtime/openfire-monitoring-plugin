package org.jivesoftware.openfire.archive;

public class ChatMarker {
    
    public enum TYPE
    {
        NONE,
        MARKABLE,
        RECEIVED,
        DISPLAYED,
        ACKNOWLEGED;
    }

    public static TYPE searchForXep0333(String stanza)
    {
        if (stanza==null)
        {
            return TYPE.NONE;
        }
        
        int idxmarkable = stanza.indexOf("<markable");
        int idxreceived= stanza.indexOf("<received");
        int idxdisplayed = stanza.indexOf("<displayed");
        int idxacknowled = stanza.indexOf("<acknowledged");
        
        if (idxmarkable!=-1)
        {
            int idxEnd = stanza.indexOf("/>",idxmarkable);
            if (idxEnd==-1)
            {
                idxEnd = stanza.indexOf("</markable>",idxmarkable);
            }
            return idxEnd!=-1?(stanza.substring(idxmarkable, idxEnd).contains("urn:xmpp:chat-markers:0")?TYPE.MARKABLE:TYPE.NONE):TYPE.NONE;
        }
        else
            if (idxreceived!=-1)
            {
                int idxEnd = stanza.indexOf("/>",idxreceived);
                if (idxEnd==-1)
                {
                    idxEnd = stanza.indexOf("</received>",idxmarkable);
                }
                return idxEnd!=-1?(stanza.substring(idxreceived, idxEnd).contains("urn:xmpp:chat-markers:0")?TYPE.RECEIVED:TYPE.NONE):TYPE.NONE;
            }
            else 
                if (idxdisplayed!=-1)
                {
                    int idxEnd = stanza.indexOf("/>",idxdisplayed);
                    if (idxEnd==-1)
                    {
                        idxEnd = stanza.indexOf("</displayed>",idxmarkable);
                    }
                    return idxEnd!=-1?(stanza.substring(idxdisplayed, idxEnd).contains("urn:xmpp:chat-markers:0")?TYPE.DISPLAYED:TYPE.NONE):TYPE.NONE;
                }
                else 
                    if (idxacknowled!=-1)
                    {
                        int idxEnd = stanza.indexOf("/>",idxacknowled);
                        if (idxEnd==-1)
                        {
                            idxEnd = stanza.indexOf("</acknowledged>",idxmarkable);
                        }
                        return idxEnd!=-1?(stanza.substring(idxacknowled, idxEnd).contains("urn:xmpp:chat-markers:0")?TYPE.ACKNOWLEGED:TYPE.NONE):TYPE.NONE;
                    }
                    else
                        return TYPE.NONE;
    }
}
