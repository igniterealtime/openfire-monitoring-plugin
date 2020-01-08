package com.reucon.openfire.plugin.archive.xep0136;

import com.reucon.openfire.plugin.archive.util.XmppDateUtil;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.JID;

import java.util.Date;

/**
 * A request to retrieve a collection.
 */
public class RetrieveRequest
{
    private JID with;
    private Date start;

    private XmppResultSet resultSet;

    public RetrieveRequest(Element listElement)
    {
        final String withValue = listElement.attributeValue("with");
        this.with = withValue == null || withValue.isEmpty() ? null : new JID(withValue);
        this.start = XmppDateUtil.parseDate(listElement.attributeValue("start"));

        Element setElement = listElement.element(QName.get("set", XmppResultSet.NAMESPACE));
        if (setElement != null)
        {
            resultSet = new XmppResultSet(setElement);
        }
    }

    public JID getWith()
    {
        return with;
    }

    public Date getStart()
    {
        return start;
    }

    public XmppResultSet getResultSet()
    {
        return resultSet;
    }
}
