/*
 * Copyright (C) 2008 Jive Software, 2026 Ignite Realtime Foundation. All rights reserved.
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

import java.awt.Color;
import java.awt.geom.Rectangle2D;
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

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.jfree.chart.JFreeChart;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.archive.PdfReportWriter;
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
                (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPluginByName(MonitoringConstants.PLUGIN_NAME).get();
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
        try (final PdfReportWriter writer = new PdfReportWriter())
        {
            int index = 0;
            int chapIndex = 0;
            for (int i = 0; i < stats.length; i++)
            {
                final Statistic stat = stats[i];

                final String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                final String dateName = JiveGlobals.formatDate(new Date(starttime)) + " - " + JiveGlobals.formatDate(new Date(endtime));

                writer.addParagraph(serverName, writer.bold(), 18, Color.BLACK);
                writer.addParagraph(dateName, writer.regular(), 14, Color.BLACK);

                writer.addBlankLine(14);
                writer.addBlankLine(14);

                writer.addParagraph(++chapIndex + ". " + stat.getName(), writer.bold(), 16, Color.BLACK);

                // total hack: no idea what tags people are going to use in the description
                // possibly recommend that we only use a <p> tag?
                String[] paragraphs = stat.getDescription().split("<p>");
                for (String s : paragraphs) {
                    writer.addParagraph(s, writer.regular(), 12, Color.BLACK);
                }
                writer.addBlankLine(12);

                final PDFormXObject chartForm = renderChartForm(writer, charts[index++], width, height);
                writer.addCenteredForm(chartForm, width, height);

                // Ensure each graph is on a new page.
                if ( i < stats.length - 1 ) {
                    writer.newPage();
                }
            }

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.save(baos);

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
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate a PDF for this graph.");
        }
    }

    /**
     * Renders a JFreeChart directly as a PDF form (vector graphics), embedded in the same
     * document as the report being written, ready to be placed on a page.
     *
     * @param writer the report the chart will be embedded into.
     * @param chart The chart to transform.
     * @param width width of the rendered chart.
     * @param height height of the rendered chart.
     * @return a PDF form object containing the rendered chart.
     */
    private static PDFormXObject renderChartForm(final PdfReportWriter writer, final JFreeChart chart, final int width, final int height) throws IOException {
        final PdfBoxGraphics2D g2 = new PdfBoxGraphics2D(writer.getDocument(), width, height);
        chart.draw(g2, new Rectangle2D.Double(0, 0, width, height));
        g2.dispose();
        return g2.getXFormObject();
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
