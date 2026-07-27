package de.leonard.flashcards.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;

@WebMvcTest(DocumentController.class)
@Import(ApiExceptionHandler.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService service;

    @Test
    void uploadReturnsCreatedWithMetadata() throws Exception {
        Document document = extractedDocument("skript.pdf", "Inhalt des Skripts");
        given(service.upload(any())).willReturn(document);

        mockMvc.perform(multipart("/api/documents").file(pdfPart()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/" + document.getId()))
                .andExpect(jsonPath("$.filename").value("skript.pdf"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.charCount").value("Inhalt des Skripts".length()))
                // Der Volltext gehoert nicht in die Antwort.
                .andExpect(jsonPath("$.extractedText").doesNotExist());
    }

    @Test
    void uploadOfEmptyFileReturnsBadRequest() throws Exception {
        willThrow(new InvalidUploadException("Die Datei ist leer.")).given(service).upload(any());

        mockMvc.perform(multipart("/api/documents").file(pdfPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Datei ist leer."));
    }

    @Test
    void listReturnsDocumentsNewestFirst() throws Exception {
        given(service.findAll()).willReturn(List.of(
                extractedDocument("neu.pdf", "zwei"),
                extractedDocument("alt.docx", "eins")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].filename").value("neu.pdf"))
                .andExpect(jsonPath("$[1].filename").value("alt.docx"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", id)).andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    @Test
    void deleteOfUnknownDocumentReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new DocumentNotFoundException(id)).given(service).delete(id);

        mockMvc.perform(delete("/api/documents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Dokument nicht gefunden: " + id));
    }

    private Document extractedDocument(String filename, String text) {
        Document document = new Document(filename, "application/pdf", 1234L);
        document.markExtracted(text);
        return document;
    }

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile(
                "file", "skript.pdf", "application/pdf", "inhalt".getBytes(StandardCharsets.UTF_8));
    }
}
