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

import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a simple, multi-page PDF report consisting of left-aligned, word-wrapped text and
 * (optionally) an embedded chart, with a small footer logo and rule drawn on every page.
 * <p>
 * This is a minimal, purpose-built replacement for the layout functionality that the plugin
 * used to get "for free" from the iText7 layout API. It's written directly against Apache
 * PDFBox, as no Apache-licensed equivalent of iText's flowing layout engine is bundled with
 * the plugin (see OF-issue about AGPL/GPL licensed PDF dependencies).
 * <p>
 * Text is set in an embedded DejaVu Sans (Bitstream Vera license), rather than a base-14
 * Helvetica, so that archived conversations containing non-Latin scripts (Cyrillic, Greek,
 * accented Latin, etc.) render correctly instead of throwing when a character falls outside
 * WinAnsiEncoding. Characters DejaVu can't cover (chiefly CJK) fall back to an embedded Noto
 * Sans CJK, loaded lazily since most conversations never need it. Anything neither font can
 * represent (e.g. emoji) is substituted with '?' rather than failing PDF generation outright.
 */
public class PdfReportWriter implements Closeable {

    private static final Logger Log = LoggerFactory.getLogger(PdfReportWriter.class);

    private static final float MARGIN = 36f;
    private static final float LEADING_MULTIPLIER = 1.2f;
    private static final float FOOTER_RESERVE = 20f;
    private static final float FOOTER_LOGO_HEIGHT = 20f;

    private final PDDocument document = new PDDocument();
    private final PDFormXObject footerLogo;
    private final PDFont regularFont;
    private final PDFont boldFont;
    private final PDFont obliqueFont;

    private PDFont cjkFont;
    private boolean cjkFontLoadAttempted;

    private PDPageContentStream contentStream;
    private float cursorX;
    private float cursorY;

    public PdfReportWriter() throws IOException {
        this.regularFont = loadFont("fonts/DejaVuSans.ttf", Standard14Fonts.FontName.HELVETICA);
        this.boldFont = loadFont("fonts/DejaVuSans-Bold.ttf", Standard14Fonts.FontName.HELVETICA_BOLD);
        this.obliqueFont = loadFont("fonts/DejaVuSans-Oblique.ttf", Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        this.footerLogo = loadFooterLogo();
        newPage();
    }

    /** A regular-weight font able to render most non-CJK scripts. */
    public PDFont regular() {
        return regularFont;
    }

    /** A bold-weight font able to render most non-CJK scripts. */
    public PDFont bold() {
        return boldFont;
    }

    /** An oblique-weight font able to render most non-CJK scripts. */
    public PDFont oblique() {
        return obliqueFont;
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
            for (Run segment : splitByFontCoverage(run)) {
                final String[] lines = segment.text.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    writeLineFragment(lines[i], segment.font, segment.size, segment.color);
                    if (i < lines.length - 1) {
                        newLine(segment.size);
                    }
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
        if (footerLogo != null) {
            final float scale = FOOTER_LOGO_HEIGHT / footerLogo.getBBox().getHeight();
            final AffineTransform at = new AffineTransform();
            at.translate(MARGIN, 4);
            at.scale(scale, scale);
            contentStream.saveGraphicsState();
            contentStream.transform(new Matrix(at));
            contentStream.drawForm(footerLogo);
            contentStream.restoreGraphicsState();
        }
        contentStream.setStrokingColor(new Color(156, 156, 156));
        contentStream.setLineWidth(2);
        final float lineY = MARGIN - 2;
        contentStream.moveTo(MARGIN, lineY);
        contentStream.lineTo(PDRectangle.A4.getWidth() - MARGIN, lineY);
        contentStream.stroke();
    }

    /**
     * Splits a run into consecutive sub-runs, each assigned whichever font can actually render
     * it: the run's own font where possible, else the (lazily-loaded) CJK fallback font, else the
     * run's own font with '?' substituted so text never fails to render. '\n' rides along with
     * whatever sub-run it falls in without affecting font selection, since it's a structural line
     * break for {@link #addRuns} rather than a glyph to be drawn.
     */
    private List<Run> splitByFontCoverage(Run run) throws IOException {
        final List<Run> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        PDFont currentFont = run.font;
        int i = 0;
        while (i < run.text.length()) {
            final int codePoint = run.text.codePointAt(i);
            final int charCount = Character.charCount(codePoint);
            if (codePoint == '\n') {
                current.append('\n');
                i += charCount;
                continue;
            }
            final PDFont resolved = resolveFont(run.font, codePoint);
            final String piece = canEncode(resolved, codePoint) ? new String(Character.toChars(codePoint)) : "?";
            if (current.length() > 0 && resolved != currentFont) {
                segments.add(new Run(current.toString(), currentFont, run.size, run.color));
                current = new StringBuilder();
            }
            currentFont = resolved;
            current.append(piece);
            i += charCount;
        }
        if (current.length() > 0) {
            segments.add(new Run(current.toString(), currentFont, run.size, run.color));
        }
        return segments.isEmpty() ? List.of(run) : segments;
    }

    /** The run's own font if it can encode the character, else the CJK fallback, else the run's own font. */
    private PDFont resolveFont(PDFont primary, int codePoint) throws IOException {
        if (canEncode(primary, codePoint)) {
            return primary;
        }
        final PDFont cjk = getCjkFont();
        if (cjk != null && canEncode(cjk, codePoint)) {
            return cjk;
        }
        return primary;
    }

    private boolean canEncode(PDFont font, int codePoint) {
        try {
            font.encode(new String(Character.toChars(codePoint)));
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Loaded on first use, since most conversations never need CJK glyph coverage. */
    private PDFont getCjkFont() throws IOException {
        if (!cjkFontLoadAttempted) {
            cjkFontLoadAttempted = true;
            cjkFont = loadOptionalFont("fonts/NotoSansCJKsc-Regular.otf");
        }
        return cjkFont;
    }

    private PDFont loadFont(String resourcePath, Standard14Fonts.FontName fallback) {
        try {
            final URL resource = PdfReportWriter.class.getClassLoader().getResource(resourcePath);
            if (resource == null) {
                Log.warn("Font resource '{}' not found, falling back to {}", resourcePath, fallback);
                return new PDType1Font(fallback);
            }
            try (InputStream in = resource.openStream()) {
                return PDType0Font.load(document, in, true);
            }
        } catch (IOException e) {
            Log.warn("Unable to load font '" + resourcePath + "', falling back to " + fallback, e);
            return new PDType1Font(fallback);
        }
    }

    /**
     * Unlike {@link #loadFont}, there's no meaningful fallback font for this slot: null means CJK
     * renders as '?'. Loaded via {@link OTFParser} rather than the plain {@code PDType0Font.load}
     * used for the DejaVu fonts, because Noto Sans CJK is a CFF-flavored OpenType font (signature
     * "OTTO"), which PDFBox's TrueType-outline loader rejects. PDFBox also can't subset CFF-flavored
     * fonts, so this embeds the font in full (~16MB) the first time a PDF actually needs a CJK
     * glyph; conversations that don't need CJK never load it, since it's only fetched on first use.
     */
    private PDFont loadOptionalFont(String resourcePath) {
        try {
            final URL resource = PdfReportWriter.class.getClassLoader().getResource(resourcePath);
            if (resource == null) {
                Log.warn("Font resource '{}' not found; CJK text will render as '?'", resourcePath);
                return null;
            }
            try (InputStream in = resource.openStream()) {
                final OpenTypeFont otf = new OTFParser().parse(RandomAccessReadBuffer.createBufferFromStream(in));
                return PDType0Font.load(document, otf, false);
            }
        } catch (IOException e) {
            Log.warn("Unable to load font '" + resourcePath + "'; CJK text will render as '?'", e);
            return null;
        }
    }

    /**
     * Pre-rendered from https://www.igniterealtime.org/fans/logo-openfire.svg, since this
     * graphic never changes between exports.
     * <p>
     * To regenerate after an upstream logo change: parse the SVG into a GVT tree (e.g. via
     * Apache Batik's {@code SAXSVGDocumentFactory} + {@code GVTBuilder}), paint that onto a
     * {@code PdfBoxGraphics2D} canvas sized to the SVG's own bounds (the same technique
     * {@code GraphServlet} uses to render JFreeChart charts), wrap the result in a single-page
     * PDF sized to those same bounds, and replace this file. Batik is only needed for this
     * one-off regeneration step; it is not, and should not become, a project dependency.
     */
    private PDFormXObject loadFooterLogo() {
        try {
            final URL resource = PdfReportWriter.class.getClassLoader().getResource("images/logo-openfire.pdf");
            if (resource == null) {
                Log.warn("Footer logo resource not found");
                return null;
            }
            try (InputStream in = resource.openStream();
                 PDDocument logoDoc = Loader.loadPDF(RandomAccessReadBuffer.createBufferFromStream(in))) {
                return new LayerUtility(document).importPageAsForm(logoDoc, 0);
            }
        } catch (IOException e) {
            Log.warn("Unable to load footer logo", e);
            return null;
        }
    }
}
