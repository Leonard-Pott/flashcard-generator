package de.leonard.flashcards.export;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping("/api/decks/{deckId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID deckId,
            @RequestParam(defaultValue = "csv") String format) {

        ExportService.Export export = service.export(deckId, ExportFormat.fromCode(format));

        // filename* sorgt dafuer, dass auch Umlaute im Namen ankommen.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.filename())
                .build();

        // Content-Disposition ist in CorsConfig fuer das Frontend freigegeben.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, export.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(export.content());
    }
}
