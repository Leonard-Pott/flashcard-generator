package de.leonard.flashcards.document;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * Extrahiert Text aus beliebigen von Tika unterstuetzten Formaten (PDF, DOCX, PPTX,
 * TXT, Markdown). Tika erkennt den Typ selbst; der vom Browser gemeldete Content-Type
 * dient nur als Rueckfallwert.
 */
@Component
public class TikaTextExtractor {

    /** Ohne Limit parsen; der Default von 100.000 Zeichen wuerde grosse PDFs abschneiden. */
    private static final int NO_WRITE_LIMIT = -1;

    private static final String GENERIC_MIME_TYPE = "application/octet-stream";

    private final AutoDetectParser parser = new AutoDetectParser();

    public ExtractionResult extract(InputStream content, String filename, String declaredMimeType) {
        BodyContentHandler handler = new BodyContentHandler(NO_WRITE_LIMIT);
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }

        try {
            parser.parse(content, handler, metadata, new ParseContext());
        } catch (IOException | SAXException | org.apache.tika.exception.TikaException e) {
            throw new TextExtractionException("Text konnte nicht extrahiert werden: " + e.getMessage(), e);
        }

        return new ExtractionResult(
                normalize(handler.toString()),
                bestMimeType(metadata.get(Metadata.CONTENT_TYPE), declaredMimeType));
    }

    /**
     * Tikas Erkennung gewinnt, weil Browser haeufig falsche oder gar keine Typen melden.
     * Bleibt sie unbestimmt (octet-stream, etwa bei Markdown), ist die Angabe des
     * Browsers immer noch besser als nichts.
     */
    private String bestMimeType(String detected, String declared) {
        String cleanedDetected = detected == null ? null : stripParameters(detected);
        String cleanedDeclared = declared == null ? null : stripParameters(declared);

        boolean detectionIsVague = cleanedDetected == null || GENERIC_MIME_TYPE.equals(cleanedDetected);
        if (detectionIsVague && cleanedDeclared != null && !GENERIC_MIME_TYPE.equals(cleanedDeclared)) {
            return cleanedDeclared;
        }
        return cleanedDetected != null ? cleanedDetected : GENERIC_MIME_TYPE;
    }

    /**
     * Vereinheitlicht den Rohtext: Windows-Zeilenenden, Leerzeichen am Zeilenende und
     * lange Leerzeilenketten (typisch fuer PDF-Seitenumbrueche) werden zusammengefasst.
     */
    private String normalize(String raw) {
        return raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    /** Aus "text/plain; charset=UTF-8" wird "text/plain". */
    private String stripParameters(String contentType) {
        int separator = contentType.indexOf(';');
        return separator < 0 ? contentType.strip() : contentType.substring(0, separator).strip();
    }

    public record ExtractionResult(String text, String mimeType) {
    }
}
