package de.leonard.flashcards.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Laeuft gegen ein echtes Postgres und prueft damit zugleich, dass die
 * Flyway-Migration und das JPA-Mapping zusammenpassen (spring.jpa.hibernate.ddl-auto
 * steht auf validate). Benoetigt ein laufendes Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DocumentPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DocumentRepository repository;

    @Test
    void storesTextFarBeyondTheDefaultColumnLength() {
        // Der Default waere VARCHAR(255) - genau der Fallstrick, den dieser Test absichert.
        String longText = "Lorem ipsum dolor sit amet. ".repeat(5000);
        Document document = new Document("skript.pdf", "application/pdf", 2_000_000L);
        document.markExtracted(longText);

        repository.saveAndFlush(document);
        Document reloaded = repository.findById(document.getId()).orElseThrow();

        assertThat(reloaded.getExtractedText()).hasSize(longText.length());
        assertThat(reloaded.getCharCount()).isEqualTo(longText.length());
        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
    }

    @Test
    void storesFailureReason() {
        Document document = new Document("scan.pdf", "application/pdf", 500L);
        document.markFailed("Es wurde kein Text gefunden.");

        repository.saveAndFlush(document);
        Document reloaded = repository.findById(document.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(reloaded.getErrorMessage()).isEqualTo("Es wurde kein Text gefunden.");
        assertThat(reloaded.getExtractedText()).isNull();
    }

    @Test
    @Sql(statements = "DELETE FROM documents")
    void listsNewestUploadFirst() throws InterruptedException {
        Document older = new Document("alt.txt", "text/plain", 10L);
        older.markExtracted("alt");
        repository.saveAndFlush(older);

        Thread.sleep(10);

        Document newer = new Document("neu.txt", "text/plain", 10L);
        newer.markExtracted("neu");
        repository.saveAndFlush(newer);

        assertThat(repository.findAllByOrderByUploadedAtDesc())
                .extracting(Document::getFilename)
                .containsExactly("neu.txt", "alt.txt");
    }
}
