/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

/**
 * Builds a simple, multi-page PDF report consisting of left-aligned, word-wrapped text and
 * (optionally) an embedded chart, with a small footer image and rule drawn on every page.
 * <p>
 * This is a minimal, purpose-built replacement for the layout functionality that the plugin
 * used to get "for free" from the iText7 layout API. It's written directly against Apache
 * PDFBox, as no Apache-licensed equivalent of iText's flowing layout engine is bundled with
 * the plugin (see OF-issue about AGPL/GPL licensed PDF dependencies).
 */
public class PdfReportWriter implements Closeable {

    private static final Logger Log = LoggerFactory.getLogger(PdfReportWriter.class);

    public static final PDFont HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    public static final PDFont HELVETICA_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    public static final PDFont HELVETICA_OBLIQUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final float MARGIN = 36f;
    private static final float LEADING_MULTIPLIER = 1.2f;
    private static final float FOOTER_RESERVE = 20f;

    private final PDDocument document = new PDDocument();
    private final PDImageXObject footerImage;

    private PDPageContentStream contentStream;
    private float cursorX;
    private float cursorY;

    public PdfReportWriter() throws IOException {
        this.footerImage = loadFooterImage();
        newPage();
    }

    /**
     * A run of text sharing a single font/size/color. A literal '\n' inside the text forces a
     * line break within (or across) runs, allowing multiple differently-styled runs to flow
     * onto the same visual line, the way iText's {@code Paragraph}/{@code Text} did.
     */
    public static final class Run {
        private final String text;
        private final PDFont font;
        private final float size;
        private final Color color;

        public Run(String text, PDFont font, float size, Color color) {
            this.text = text;
            this.font = font;
            this.size = size;
            this.color = color;
        }
    }

    public PDDocument getDocument() {
        return document;
    }

    /**
     * Starts a new page. The footer is drawn immediately, so callers only need to worry about
     * the flowing content area.
     */
    public void newPage() throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
        final PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        contentStream = new PDPageContentStream(document, page);
        drawFooter();
        cursorX = MARGIN;
        cursorY = PDRectangle.A4.getHeight() - MARGIN;
    }

    /** Writes a single-style paragraph, word-wrapped to the page width, then moves to a new line. */
    public void addParagraph(String text, PDFont font, float size, Color color) throws IOException {
        addRuns(List.of(new Run(text, font, size, color)));
        newLine(size);
    }

    /** Moves the cursor down by one blank line of the given size, for spacing between blocks. */
    public void addBlankLine(float size) throws IOException {
        newLine(size);
    }

    /** Writes a sequence of differently-styled runs that flow together, wrapping as needed. */
    public void addRuns(List<Run> runs) throws IOException {
        for (Run run : runs) {
            final String[] lines = run.text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                writeLineFragment(lines[i], run.font, run.size, run.color);
                if (i < lines.length - 1) {
                    newLine(run.size);
                }
            }
        }
    }

    /** Draws a previously rendered form (see {@link #getDocument()}) centered at the current position. */
    public void addCenteredForm(PDFormXObject form, float width, float height) throws IOException {
        if (cursorY - height < MARGIN + FOOTER_RESERVE) {
            newPage();
        }
        final float contentWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        final float x = MARGIN + Math.max(0, (contentWidth - width) / 2f);
        final float y = cursorY - height;
        contentStream.saveGraphicsState();
        contentStream.transform(Matrix.getTranslateInstance(x, y));
        contentStream.drawForm(form);
        contentStream.restoreGraphicsState();
        cursorY = y;
    }

    /** Finalizes the document and writes it to the given stream. The writer cannot be reused afterwards. */
    public void save(OutputStream out) throws IOException {
        if (contentStream != null) {
            contentStream.close();
            contentStream = null;
        }
        document.save(out);
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    private void writeLineFragment(String text, PDFont font, float size, Color color) throws IOException {
        if (text.isEmpty()) {
            return;
        }
        final String[] words = text.split(" ", -1);
        final float maxX = PDRectangle.A4.getWidth() - MARGIN;
        for (int i = 0; i < words.length; i++) {
            final String toDraw = i > 0 ? " " + words[i] : words[i];
            if (toDraw.isEmpty()) {
                continue;
            }
            final float width = font.getStringWidth(toDraw) / 1000f * size;
            if (cursorX + width > maxX && cursorX > MARGIN) {
                newLine(size);
            }
            drawText(toDraw, font, size, color);
            cursorX += width;
        }
    }

    private void drawText(String text, PDFont font, float size, Color color) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, size);
        contentStream.setNonStrokingColor(color);
        contentStream.newLineAtOffset(cursorX, cursorY);
        contentStream.showText(text);
        contentStream.endText();
    }

    private void newLine(float size) throws IOException {
        cursorX = MARGIN;
        cursorY -= size * LEADING_MULTIPLIER;
        if (cursorY < MARGIN + FOOTER_RESERVE) {
            newPage();
        }
    }

    private void drawFooter() throws IOException {
        if (footerImage != null) {
            contentStream.drawImage(footerImage, MARGIN, 4);
        }
        contentStream.setStrokingColor(new Color(156, 156, 156));
        contentStream.setLineWidth(2);
        final float lineY = MARGIN - 2;
        contentStream.moveTo(MARGIN, lineY);
        contentStream.lineTo(PDRectangle.A4.getWidth() - MARGIN, lineY);
        contentStream.stroke();
    }

    private PDImageXObject loadFooterImage() {
        try {
            final URL resource = PdfReportWriter.class.getClassLoader().getResource("images/pdf_generatedbyof.gif");
            if (resource == null) {
                return null;
            }
            try (InputStream in = resource.openStream()) {
                final BufferedImage image = ImageIO.read(in);
                return image == null ? null : LosslessFactory.createFromImage(document, image);
            }
        } catch (IOException e) {
            Log.warn("Unable to load PDF footer image", e);
            return null;
        }
    }
}
