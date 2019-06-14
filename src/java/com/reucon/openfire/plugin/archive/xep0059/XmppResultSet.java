package com.reucon.openfire.plugin.archive.xep0059;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://www.xmpp.org/extensions/xep-0059.html">XEP-0059</a> result set.
 */
public class XmppResultSet
{
    private static final Logger Log = LoggerFactory.getLogger( XmppResultSet.class );

    public static String NAMESPACE = "http://jabber.org/protocol/rsm";
    private String after;
    private String before;
    private Integer index;
    private Integer max;
    private String first;
    private Integer firstIndex;
    private String last;
    private Integer count;
    private boolean complete;
    boolean pagingBackwards = false;

    public XmppResultSet(Element setElement)
    {
        if (setElement.element("after") != null)
        {
            after = setElement.elementText( "after" );
            if ( after.isEmpty() ) {
                after = null;
            }
        }
        if (setElement.element("before") != null)
        {
            before = setElement.elementText("before");

            // If 'before' is set it's an indication that backwards paging is desired, even if it's empty.
            if ( after == null && setElement.element( "before" ) != null ) {
                pagingBackwards = true;
            }

            if ( before.isEmpty() ) {
                before = null;
            }
        }
        if (setElement.element("max") != null)
        {
            try
            {
                max = Integer.parseInt(setElement.elementText("max"));
                if (max < 0)
                {
                    max = null;
                }
            }
            catch (Exception e)
            {
                Log.debug( "Unable to parse value '{}' as a RSM 'max' value.", setElement.elementText("max") );
            }
        }
        if (setElement.element("index") != null)
        {
            try
            {
                index = Integer.parseInt(setElement.elementText("index"));
                if (index < 0)
                {
                    index = null;
                }
            }
            catch (Exception e)
            {
                Log.debug( "Unable to parse value '{}' as a RSM 'index' value.", setElement.elementText("index") );
            }
        }
    }

    public String getAfter()
    {
        return after;
    }

    public String getBefore()
    {
        return before;
    }

    /**
     * Returns the index of the first element to return.
     *
     * @return the index of the first element to return.
     */
    public Integer getIndex()
    {
        return index;
    }

    /**
     * Returns the maximum number of items to return.
     *
     * @return the maximum number of items to return.
     */
    public Integer getMax()
    {
        return max;
    }

    /**
     * Returns the total size of the result set.
     *
     * @return the total size of the result set.
     */
    public Integer getCount() {
        return count;
    }

    /**
     * Returns whether the result set is complete (last page of results).
     *
     * @return whether the result set is complete.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Sets the id of the first element returned.
     *
     * @param first the id of the first element returned.
     */
    public void setFirst(String first)
    {
        if (first != null && first.isEmpty()) {
            first = null;
        }
        this.first = first;
    }

    /**
     * Sets the index of the first element returned.
     *
     * @param firstIndex the index of the first element returned.
     */
    public void setFirstIndex(Integer firstIndex)
    {
        this.firstIndex = firstIndex;
    }

    /**
     * Sets the id of the last element returned.
     *
     * @param last the id of the last element returned.
     */
    public void setLast(String last)
    {
        if (last != null && last.isEmpty()) {
            last = null;
        }
        this.last = last;
    }

    /**
     * Sets the number of elements returned.
     *
     * @param count the number of elements returned.
     */
    public void setCount(Integer count)
    {
        this.count = count;
    }

    /**
     * Sets whether the result set is complete (used by last page of results)
     *
     * @param complete true if the result set is on its last page, otherwise 'false'.
     */
    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /**
     * Returns whether the result set is a request to page backwards through the result set.
     *
     * @return whether the result set is being paged to backwards..
     */
    public boolean isPagingBackwards() {
        return pagingBackwards;
    }

    public Element createResultElement()
    {
        final Element set;

        set = DocumentFactory.getInstance().createElement("set", NAMESPACE);
        if (first != null)
        {
            final Element firstElement;
            firstElement = set.addElement("first");
            firstElement.setText(first);
            if (firstIndex != null)
            {
                firstElement.addAttribute("index", firstIndex.toString());
            }
        }
        if (last != null)
        {
            set.addElement("last").setText(last);
        }
        if (count != null)
        {
            set.addElement("count").setText(count.toString());
        }

        return set;
    }

    @Override
    public String toString()
    {
        return "XmppResultSet{" +
            "after=" + after +
            ", before=" + before +
            ", index=" + index +
            ", max=" + max +
            ", first=" + first +
            ", firstIndex=" + firstIndex +
            ", last=" + last +
            ", count=" + count +
            ", complete=" + complete +
            '}';
    }
}
