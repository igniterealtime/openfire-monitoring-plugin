package com.reucon.openfire.plugin.archive.xep0313;

import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.JID;

import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;

/**
 * A request to query an archive
 */
public class QueryRequest {

    private String queryid;
    private DataForm dataForm;
    private XmppResultSet resultSet;
    private final JID archive;
    private boolean flipPage;

    public QueryRequest(Element queryElement, JID archive) {

        this.archive = archive;

        if (queryElement.attribute("queryid") != null)
        {
            this.queryid = queryElement.attributeValue("queryid");
        }

        Element xElement = queryElement.element(QName.get("x", DataForm.NAMESPACE));
        if(xElement != null) {
            this.dataForm = new DataForm(xElement);
        }

        Element setElement = queryElement.element(QName.get("set", XmppResultSet.NAMESPACE));
        if (setElement != null)
        {
            resultSet = new XmppResultSet(setElement);
        }

        // <flip-page/> is an empty child of <query/> (mam:2#extended); parsing only for now.
        this.flipPage = queryElement.element("flip-page") != null;
    }

    public String getQueryid() {
        return queryid;
    }

    public DataForm getDataForm() {
        return dataForm;
    }

    public XmppResultSet getResultSet() {
        return resultSet;
    }

    public JID getArchive() {
        return archive;
    }

    /**
     * Whether the query requested {@code <flip-page/>} (mam:2#extended).
     *
     * @return true if flip-page was present on the query element.
     */
    public boolean isFlipPage() {
        return flipPage;
    }
}
