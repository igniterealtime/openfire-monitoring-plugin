/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.archive;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.Leading;
import com.itextpdf.layout.property.Property;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Utility class for asynchronous web calls for archiving tasks.
 *
 * @author Derek DeMoro
 */
public class ConversationUtils {

    private static final Logger Log = LoggerFactory.getLogger(ConversationUtils.class);
            
    /**
     * Returns the status of the rebuilding of the messaging/metadata archives. This is done
     * asynchronously.
     *
     * @return the status the rebuilding (0 - 100) where 100 is complete.
     */
    public int getBuildProgress() {
        // Get handle on the Monitoring plugin
        MonitoringPlugin plugin =
            (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
                    MonitoringConstants.NAME);

        ArchiveIndexer archiveIndexer = plugin.getArchiveIndexer();

        Future<Integer> future = archiveIndexer.getIndexRebuildProgress();
        if (future != null) {
            try {
                return future.get();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }

        return -1;
    }

    public ConversationInfo getConversationInfo(long conversationID, boolean formatParticipants) {
        // Create ConversationInfo bean
        ConversationInfo info = new ConversationInfo();

        // Get handle on the Monitoring plugin
        MonitoringPlugin plugin =
            (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
                    MonitoringConstants.NAME);

        ConversationManager conversationmanager = plugin.getConversationManager();

        try {
            Conversation conversation = conversationmanager.getConversation(conversationID);
            info = toConversationInfo(conversation, formatParticipants);
        }
        catch (NotFoundException e) {
            Log.error(e.getMessage(), e);
        }

        return info;
    }


    /**
     * Retrieves all the existing conversations from the system.
     *
     * @return a Map of ConversationInfo objects.
     */
    public Map<String, ConversationInfo> getConversations(boolean formatParticipants) {
        Map<String, ConversationInfo> cons = new HashMap<String, ConversationInfo>();
        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager()
            .getPlugin(MonitoringConstants.NAME);
        ConversationManager conversationManager = plugin.getConversationManager();
        Collection<Conversation> conversations = conversationManager.getConversations();
        List<Conversation> lConversations =
            Arrays.asList(conversations.toArray(new Conversation[conversations.size()]));
        for (Iterator<Conversation> i = lConversations.iterator(); i.hasNext();) {
            Conversation con = i.next();
            ConversationInfo info = toConversationInfo(con, formatParticipants);
            cons.put(Long.toString(con.getConversationID()), info);
        }
        return cons;
    }

    public ByteArrayOutputStream getConversationPDF(Conversation conversation) throws IOException {
        Map<JID, Color> colorMap = new HashMap<>();
        if (conversation != null) {
            Collection<JID> set = conversation.getParticipants();
            int count = 0;
            for (JID jid : set) {
                if (conversation.getRoom() == null) {
                    if (count == 0) {
                        colorMap.put(jid, ColorConstants.BLUE);
                    }
                    else {
                        colorMap.put(jid, ColorConstants.RED);
                    }
                    count++;
                }
                else {
                    colorMap.put(jid, ColorConstants.BLACK);
                }
            }
        }


        return buildPDFContent(conversation, colorMap);
    }

    private ByteArrayOutputStream buildPDFContent(Conversation conversation, Map<JID, Color> colorMap) throws IOException {

        try ( final ByteArrayOutputStream baos = new ByteArrayOutputStream();
              final PdfWriter writer = new PdfWriter(baos);
              final PdfDocument pdfDocument = new PdfDocument(writer)
        )
        {
            pdfDocument.setDefaultPageSize( PageSize.A4 );
            final Document document = new Document(pdfDocument);

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFEventListener(document));
            document.setProperty(Property.LEADING, new Leading(Leading.MULTIPLIED, 1.0f));

            document.add( new Paragraph() );
            document.add(
                new Paragraph( LocaleUtils.getLocalizedString("archive.search.pdf.title", MonitoringConstants.NAME) )
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize( 18 )
            );
            document.add( new Paragraph().add(new Text("\n")) );

            final ConversationInfo coninfo = new ConversationUtils().getConversationInfo(conversation.getConversationID(), false);

            String participantsDetail;
            if (coninfo.getAllParticipants() == null) {
                participantsDetail = coninfo.getParticipant1() + ", " + coninfo.getParticipant2();
            }
            else {
                participantsDetail = String.valueOf(coninfo.getAllParticipants().length);
            }

            document.add(
                new Paragraph( LocaleUtils.getLocalizedString("archive.search.pdf.participants", MonitoringConstants.NAME) + " " + participantsDetail + '\n')
                    .add( LocaleUtils.getLocalizedString("archive.search.pdf.startdate", MonitoringConstants.NAME) + " " + coninfo.getDate() + '\n')
                    .add( LocaleUtils.getLocalizedString("archive.search.pdf.duration", MonitoringConstants.NAME) + " " + coninfo.getDuration() + '\n')
                    .add( LocaleUtils.getLocalizedString("archive.search.pdf.messagecount", MonitoringConstants.NAME) + " " + conversation.getMessageCount() + '\n' )
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize(12)
            );

            document.add( new Paragraph().add(new Text("\n")));

            final Paragraph messageParagraph = new Paragraph();
            for (ArchivedMessage message : conversation.getMessages())
            {
                String time = JiveGlobals.formatTime(message.getSentDate());
                String from = message.getFromJID().getNode();
                String to = message.getIsPMforNickname(); // Only non-null when this is a Private Message sent in a MUC.
                if (conversation.getRoom() != null) {
                    from = message.getToJID().getResource();
                }
                String body = message.getBody();
                String prefix;

                if (!message.isRoomEvent()) {
                    if (to == null) {
                        prefix = "[" + time + "] " + from + ":  ";
                    } else {
                        prefix = "[" + time + "] " + from + " -> " + to + ":  ";
                    }
                    Color color = colorMap.get(message.getFromJID());
                    if (color == null) {
                        color = colorMap.get(message.getFromJID().asBareJID());
                    }
                    if (color == null) {
                        color = ColorConstants.BLACK;
                    }

                    messageParagraph.add(new Text(prefix).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)).setFontColor(color));
                    messageParagraph.add(new Text(body).setFontColor(ColorConstants.BLACK));
                }
                else {
                    prefix = "[" + time + "] ";
                    messageParagraph.add( new Text(prefix)).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)).setFontColor(ColorConstants.MAGENTA);
                    messageParagraph.add( new Text(body).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)).setFontColor(ColorConstants.MAGENTA));
                }
                messageParagraph.add(new Text("\n"));
            }

            document.add(messageParagraph);

            document.close();
            return baos;
        }
        catch (Exception e) {
            Log.error("error creating PDF document: " + e.getMessage(), e);
            return null;
        }
    }

    private ConversationInfo toConversationInfo(Conversation conversation,
                                                boolean formatParticipants) {
        final ConversationInfo info = new ConversationInfo();
        // Set participants
        Collection<JID> col = conversation.getParticipants();

        if (conversation.getRoom() == null) {
            JID user1 = (JID)col.toArray()[0];
            info.setParticipant1(formatJID(formatParticipants, user1));
            JID user2 = (JID)col.toArray()[1];
            info.setParticipant2(formatJID(formatParticipants, user2));
        }
        else {
            info.setConversationID(conversation.getConversationID());
            JID[] occupants = col.toArray(new JID[col.size()]);
            String[] jids = new String[col.size()];
            for (int i = 0; i < occupants.length; i++) {
                jids[i] = formatJID(formatParticipants, occupants[i]);
            }
            info.setAllParticipants(jids);
        }

        Map<String, String> cssLabels = new HashMap<String, String>();
        int count = 0;
        for (JID jid : col) {
            if (!cssLabels.containsKey(jid.toString())) {
                if (conversation.getRoom() == null) {
                    if (count % 2 == 0) {
                        cssLabels.put(jid.toBareJID(), "conversation-label2");
                    }
                    else {
                        cssLabels.put(jid.toBareJID(), "conversation-label1");
                    }
                    count++;
                }
                else {
                    cssLabels.put(jid.toString(), "conversation-label4");
                }
            }
        }

        // Set date
        info.setDate(JiveGlobals.formatDateTime(conversation.getStartDate()));
        info.setLastActivity(JiveGlobals.formatTime(conversation.getLastActivity()));
        // Create body.
        final StringBuilder builder = new StringBuilder();
        builder.append("<table width=100%>");
        for (ArchivedMessage message : conversation.getMessages()) {
            String time = JiveGlobals.formatTime(message.getSentDate());
            String from = message.getFromJID().getNode();
            String to = message.getIsPMforNickname(); // Only non-null when this is a Private Message sent in a MUC.
            if (conversation.getRoom() != null) {
                from = message.getToJID().getResource();
            }
            from = StringUtils.escapeHTMLTags(from);
            to = to == null ? null : StringUtils.escapeHTMLTags(to);
            String cssLabel = cssLabels.get(message.getFromJID().toBareJID());
            String body = StringUtils.escapeHTMLTags(message.getBody());
            builder.append("<tr valign=top>");
            if (!message.isRoomEvent()) {
                builder.append("<td width=1% nowrap class=" + cssLabel + ">").append("[").append(time).append("]").append("</td>");
                builder.append("<td width=1% class=" + cssLabel + ">").append(from);
                if (to != null) {
                    builder.append("&rarr;").append(to);
                }
                builder.append(": ").append("</td>");
                builder.append("<td class=conversation-body>").append(body).append("</td");
            }
            else {
                builder.append("<td width=1% nowrap class=conversation-label3>").append("[").append(time).append("]").append("</td>");
                builder.append("<td colspan=2 class=conversation-label3><i>").append(body).append("</i></td");
            }
            builder.append("</tr>");
        }

        if (conversation.getMessages().size() == 0) {
            builder.append("<span class=small-description>" +
                LocaleUtils.getLocalizedString("archive.search.results.archive_disabled",
                        MonitoringConstants.NAME) +
                "</a>");
        }

        info.setBody(builder.toString());

        // Set message count
        info.setMessageCount(conversation.getMessageCount());

        long duration =
            (conversation.getLastActivity().getTime() - conversation.getStartDate().getTime());
        info.setDuration(duration);

        return info;
    }

    private String formatJID(boolean html, JID jid) {
        String formattedJID;
        if (html) {
            UserManager userManager = UserManager.getInstance();
            if (XMPPServer.getInstance().isLocal(jid) &&
                userManager.isRegisteredUser(jid.getNode())) {
                formattedJID = "<a href='/user-properties.jsp?username=" +
                    jid.getNode() + "'>" + jid.toBareJID() + "</a>";
            }
            else {
                formattedJID = jid.toBareJID();
            }
        }
        else {
            formattedJID = jid.toBareJID();
        }
        return formattedJID;
    }

    /**
     * Writes a footer.
     */
    public static class PDFEventListener implements IEventHandler {
        private final Document document;

        public PDFEventListener(Document document) {
            this.document = document;
        }

        @Override
        public void handleEvent(Event event) {
            try {
                final URL resource = ConversationUtils.class.getClassLoader().getResource("images/pdf_generatedbyof.gif");
                if (resource != null) {
                    final ImageData imageData = ImageDataFactory.create(resource);

                    final PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                    final PdfDocument pdf = docEvent.getDocument();
                    final PdfPage page = docEvent.getPage();
                    final Rectangle pageSize = page.getPageSize();
                    final PdfCanvas pdfCanvas = new PdfCanvas(page.getLastContentStream(), page.getResources(), pdf);
                    float x = document.getLeftMargin();
                    float y = 4; // Counts from the bottom of the page.

                    pdfCanvas.addXObjectAt(new PdfImageXObject(imageData), x, y);

                    final SolidLine line = new SolidLine(2);
                    line.setColor(new DeviceRgb(156, 156, 156));
                    line.draw(pdfCanvas, new Rectangle(document.getLeftMargin(), document.getBottomMargin() - 2, pageSize.getWidth() - document.getRightMargin() - document.getLeftMargin(), 2));

                    pdfCanvas.release();
                }
            } catch (Exception e) {
                Log.error("error drawing PDF footer.", e);
            }
        }
    }
}
