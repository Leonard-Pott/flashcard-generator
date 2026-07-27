package de.leonard.flashcards.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final int MAX_FILENAME_LENGTH = 512;
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private final DocumentRepository repository;
    private final TikaTextExtractor extractor;

    public DocumentService(DocumentRepository repository, TikaTextExtractor extractor) {
        this.repository = repository;
        this.extractor = extractor;
    }

    @Transactional
    public Document upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidUploadException("Die Datei ist leer.");
        }

        String filename = safeFilename(file.getOriginalFilename());
        Document document = new Document(
                filename,
                file.getContentType() != null ? file.getContentType() : DEFAULT_MIME_TYPE,
                file.getSize());

        extractInto(document, file, filename);
        return repository.save(document);
    }

    private void extractInto(Document document, MultipartFile file, String filename) {
        try (InputStream content = file.getInputStream()) {
            TikaTextExtractor.ExtractionResult result =
                    extractor.extract(content, filename, file.getContentType());

            if (!StringUtils.hasText(result.text())) {
                document.markFailed("Es wurde kein Text gefunden. Bei gescannten PDFs fehlt "
                        + "die Textebene; hier hilft nur eine OCR-Vorverarbeitung.");
                log.info("Keine Textebene in '{}' gefunden", filename);
                return;
            }

            document.markExtracted(result.text());
            log.debug("'{}' extrahiert: {} Zeichen, Typ {}", filename, document.getCharCount(), result.mimeType());
        } catch (TextExtractionException | IOException e) {
            document.markFailed(e.getMessage());
            log.warn("Extraktion von '{}' fehlgeschlagen: {}", filename, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Document> findAll() {
        return repository.findAllByOrderByUploadedAtDesc();
    }

    @Transactional(readOnly = true)
    public Document findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }
        repository.deleteById(id);
    }

    /** Browser koennen ganze Pfade melden; davon bleibt nur der Dateiname uebrig. */
    private String safeFilename(String originalFilename) {
        String cleaned = StringUtils.getFilename(StringUtils.cleanPath(
                originalFilename != null ? originalFilename : ""));

        if (!StringUtils.hasText(cleaned)) {
            throw new InvalidUploadException("Die Datei hat keinen gueltigen Namen.");
        }
        return cleaned.length() > MAX_FILENAME_LENGTH
                ? cleaned.substring(0, MAX_FILENAME_LENGTH)
                : cleaned;
    }
}
