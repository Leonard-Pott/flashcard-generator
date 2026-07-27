package de.leonard.flashcards.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/**
 * Prueft die Extraktion gegen echte Dateien der Formate, die spaeter hochgeladen
 * werden. PDF, DOCX und PPTX werden dafuer im Test erzeugt.
 */
class TikaTextExtractorTest {

    private final TikaTextExtractor extractor = new TikaTextExtractor();

    @Test
    void extractsPlainText() {
        String content = "Erste Zeile\nZweite Zeile mit Umlauten: aeoeue";

        TikaTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "notizen.txt",
                "text/plain");

        assertThat(result.text()).contains("Erste Zeile").contains("Zweite Zeile");
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void extractsMarkdownAsText() {
        String content = "# Ueberschrift\n\nEin Absatz mit **fett** markiertem Text.";

        TikaTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "skript.md",
                "text/markdown");

        assertThat(result.text()).contains("Ueberschrift").contains("Ein Absatz");
    }

    @Test
    void extractsPdf() throws Exception {
        TikaTextExtractor.ExtractionResult result =
                extractor.extract(pdfWithText("ITIL Service Design"), "skript.pdf", "application/pdf");

        assertThat(result.text()).contains("ITIL Service Design");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void extractsDocx() throws Exception {
        TikaTextExtractor.ExtractionResult result = extractor.extract(
                docxWithText("Definition der Verfuegbarkeit"),
                "kapitel.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(result.text()).contains("Definition der Verfuegbarkeit");
        assertThat(result.mimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void extractsPptx() throws Exception {
        TikaTextExtractor.ExtractionResult result = extractor.extract(
                pptxWithText("Folie 1: Grundlagen"),
                "vorlesung.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");

        assertThat(result.text()).contains("Folie 1: Grundlagen");
    }

    @Test
    void fallsBackToTheDeclaredTypeWhenDetectionStaysGeneric() {
        // Markdown erkennt Tika nicht zuverlaessig; dann zaehlt die Angabe des Browsers.
        String content = "# Ueberschrift\n\nText.";

        TikaTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "skript.md",
                "text/markdown");

        assertThat(result.mimeType()).isNotEqualTo("application/octet-stream");
    }

    @Test
    void detectsTypeEvenWhenTheBrowserReportsSomethingWrong() throws Exception {
        // Browser melden fuer unbekannte Endungen gerne application/octet-stream.
        TikaTextExtractor.ExtractionResult result =
                extractor.extract(pdfWithText("Trotzdem erkannt"), "skript.pdf", "application/octet-stream");

        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }

    @Test
    void collapsesExcessiveBlankLines() {
        String content = "Absatz eins\n\n\n\n\nAbsatz zwei   \n";

        TikaTextExtractor.ExtractionResult result = extractor.extract(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                "roh.txt",
                "text/plain");

        assertThat(result.text()).isEqualTo("Absatz eins\n\nAbsatz zwei");
    }

    @Test
    void failsOnBrokenFile() {
        byte[] brokenPdf = "%PDF-1.4 kaputt".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extractor.extract(
                new ByteArrayInputStream(brokenPdf), "kaputt.pdf", "application/pdf"))
                .isInstanceOf(TextExtractionException.class);
    }

    private InputStream pdfWithText(String text) throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            pdf.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private InputStream docxWithText(String text) throws Exception {
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            docx.createParagraph().createRun().setText(text);
            docx.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private InputStream pptxWithText(String text) throws Exception {
        try (XMLSlideShow pptx = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFTextBox textBox = pptx.createSlide().createTextBox();
            textBox.setText(text);
            pptx.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
