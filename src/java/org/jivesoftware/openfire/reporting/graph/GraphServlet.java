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
package org.jivesoftware.openfire.reporting.graph;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.AreaBreakType;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.orsonpdf.PDFDocument;
import com.orsonpdf.PDFGraphics2D;
import com.orsonpdf.Page;
import org.dom4j.DocumentException;
import org.jfree.chart.JFreeChart;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ConversationUtils;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.reporting.stats.StatsViewer;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphServlet extends HttpServlet {

    private static final Logger Log = LoggerFactory.getLogger(GraphServlet.class);
    private GraphEngine graphEngine;
    private StatsViewer statsViewer;

    @Override
    public void init() throws ServletException {
        // load dependencies
        MonitoringPlugin plugin =
                (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(MonitoringConstants.NAME);
        this.graphEngine = plugin.getGraphEngine();
        this.statsViewer = plugin.getStatsViewer();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // retrieve parameters
        String statisticKey = request.getParameter("stat");
        String timePeriod = request.getParameter("timeperiod");
        String graphcolor = request.getParameter("color");
        boolean sparkLines = request.getParameter("sparkline") != null;
        boolean pdfFormat = request.getParameter("pdf") != null;
        long[] dateRange = GraphEngine.parseTimePeriod(timePeriod);
        int width;
        int height;

        if (pdfFormat) {
            // PDF A4 page = 595 wide - (50px * 2 margins) = 495
            width = ParamUtils.getIntParameter(request, "width", 495);
            height = ParamUtils.getIntParameter(request, "height", 252);
            JFreeChart[] charts;
            Statistic[] stats;
            if (request.getParameter("pdf").equalsIgnoreCase("all")) {
                String[] statKeys = statsViewer.getAllHighLevelStatKeys();
                List<String> statList = Arrays.asList(statKeys);
                Collections.sort(statList, new Comparator<String>() {
                    public int compare(String stat1, String stat2) {
                        String statName1 = statsViewer.getStatistic(stat1)[0].getName();
                        String statName2 = statsViewer.getStatistic(stat2)[0].getName();
                        return statName1.toLowerCase().compareTo(statName2.toLowerCase());
                    }
                });
                charts = new JFreeChart[statList.size()];
                stats = new Statistic[statList.size()];
                int index = 0;
                for (String statName : statList) {
                    stats[index] = statsViewer.getStatistic(statName)[0];
                    charts[index] = graphEngine.generateChart(statName, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
                    index++;
                }
            } else {
                charts = new JFreeChart[] {graphEngine.generateChart(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2])};
                stats = new Statistic[] {statsViewer.getStatistic(statisticKey)[0]};
            }
            writePDFContent(request, response, charts, stats, dateRange[0], dateRange[1], width, height);
        } else {
            byte[] chart;
            if (sparkLines) {
                width = ParamUtils.getIntParameter(request, "width", 200);
                height = ParamUtils.getIntParameter(request, "height", 50);
                chart = graphEngine.generateSparklinesGraph(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
            }
            else {
                width = ParamUtils.getIntParameter(request, "width", 590);
                height = ParamUtils.getIntParameter(request, "height", 300);
                chart = graphEngine.generateGraph(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
            }

            writeImageContent(response, chart, "image/png");
        }
    }

    private void writePDFContent(HttpServletRequest request, HttpServletResponse response, JFreeChart[] charts, Statistic[] stats, long starttime, long endtime, int width, int height)
            throws IOException
    {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final PdfWriter writer = new PdfWriter(baos);
             final PdfDocument pdfDocument = new PdfDocument(writer)
        )
        {
            pdfDocument.setDefaultPageSize( PageSize.A4 );
            final Document document = new Document(pdfDocument);
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new ConversationUtils.PDFEventListener(document));

            int index = 0;
            int chapIndex = 0;
            for (int i = 0; i < stats.length; i++)
            {
                final Statistic stat = stats[i];

                final String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                final String dateName = JiveGlobals.formatDate(new Date(starttime)) + " - " + JiveGlobals.formatDate(new Date(endtime));

                document.add( new Paragraph(serverName)
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize( 18 )
                );

                document.add( new Paragraph(dateName)
                    .setFontSize( 14 )
                );

                document.add( new Paragraph().add(new Text("\n")) );
                document.add( new Paragraph().add(new Text("\n")) );

                document.add( new Paragraph(++chapIndex + ". " + stat.getName())
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize( 16 )
                );

                // total hack: no idea what tags people are going to use in the description
                // possibly recommend that we only use a <p> tag?
                String[] paragraphs = stat.getDescription().split("<p>");
                for (String s : paragraphs) {
                    Paragraph p = new Paragraph(s);
                    document.add(p);
                }
                document.add( new Paragraph().add(new Text("\n")) );

                // Use OrsonPDF to generate PDF data from JFreeChart, then import it into iText. See https://jfree.github.io/orsonpdf/
                final PdfReader reader = new PdfReader(new ByteArrayInputStream(generateChartPDF(charts[index++], width, height)));
                final PdfDocument chartDoc = new PdfDocument(reader);
                final PdfFormXObject chart = chartDoc.getFirstPage().copyAsFormXObject(pdfDocument);
                final Image chartImage = new Image(chart);
                chartImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
                document.add(chartImage);

                // Ensure each graph is on a new page.
                if ( i < stats.length - 1 ) {
                    document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                }
            }

            document.close();

            // setting some response headers
            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");
            // setting the content type
            response.setContentType("application/pdf");
            // the contentlength is needed for MSIE!!!
            response.setContentLength(baos.size());
            // write ByteArrayOutputStream to the ServletOutputStream
            ServletOutputStream out = response.getOutputStream();
            baos.writeTo(out);
            out.flush();
        } catch (Exception e) {
            Log.error("error creating PDF document", e);
        }
    }

    /**
     * Transform the JFreeChart data into a byte array of PDF image data.
     *
     * @param chart The chart to transform.
     * @param width width of the PDF image object.
     * @param height height of the PDF image object.
     * @return PDF image data.
     */
    private static byte[] generateChartPDF(final JFreeChart chart, final int width, final int height) {
        // here we use OrsonPDF to generate PDF in a byte array
        PDFDocument doc = new PDFDocument();
        Rectangle bounds = new Rectangle(width,height);
        Page page = doc.createPage(bounds);
        PDFGraphics2D g2 = page.getGraphics2D();
        chart.draw(g2, bounds);
        return doc.getPDFBytes();
    }

    private static void writeImageContent(HttpServletResponse response, byte[] imageData, String contentType)
            throws IOException
    {
        ServletOutputStream os = response.getOutputStream();
        response.setContentType(contentType);
        os.write(imageData);
        os.flush();
        os.close();
    }
}
