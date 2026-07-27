package de.leonard.flashcards.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository repository;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, new TikaTextExtractor());
        // lenient, weil die Tests zu Loeschen und leerer Datei gar nicht speichern.
        lenient().when(repository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void storesExtractedTextAndCharCount() {
        MockMultipartFile file = textFile("skript.txt", "Kapitel 1: Grundlagen");

        Document saved = service.upload(file);

        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
        assertThat(saved.getExtractedText()).isEqualTo("Kapitel 1: Grundlagen");
        assertThat(saved.getCharCount()).isEqualTo("Kapitel 1: Grundlagen".length());
        assertThat(saved.getErrorMessage()).isNull();
    }

    @Test
    void marksDocumentFailedWhenNoTextWasFound() {
        // Eine leere, aber gueltige Textdatei steht stellvertretend fuer ein
        // gescanntes PDF ohne Textebene.
        MockMultipartFile file = textFile("scan.txt", "   \n\n  ");

        Document saved = service.upload(file);

        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(saved.getExtractedText()).isNull();
        assertThat(saved.getCharCount()).isZero();
        assertThat(saved.getErrorMessage()).contains("kein Text");
    }

    @Test
    void marksDocumentFailedWhenExtractionThrows() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "kaputt.pdf", "application/pdf", "%PDF-1.4 kaputt".getBytes(StandardCharsets.UTF_8));

        Document saved = service.upload(file);

        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(saved.getErrorMessage()).isNotBlank();
    }

    @Test
    void keepsOnlyTheFilenameWhenTheBrowserSendsAPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\Users\\leona\\Desktop\\skript.txt", "text/plain",
                "Inhalt".getBytes(StandardCharsets.UTF_8));

        service.upload(file);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFilename()).isEqualTo("skript.txt");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "leer.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("leer");
        verify(repository, never()).save(any());
    }

    @Test
    void deleteFailsForUnknownId() {
        UUID id = UUID.randomUUID();
        given(repository.existsById(id)).willReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).deleteById(id);
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
