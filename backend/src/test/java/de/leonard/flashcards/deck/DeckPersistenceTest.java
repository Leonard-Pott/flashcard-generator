package de.leonard.flashcards.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import de.leonard.flashcards.document.Document;
import de.leonard.flashcards.document.DocumentRepository;

/**
 * Prueft Mapping, Kaskadierung und Sortierung gegen ein echtes Postgres.
 * Benoetigt ein laufendes Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DeckPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FolderRepository folderRepository;

    @BeforeEach
    void clean() {
        cardRepository.deleteAll();
        deckRepository.deleteAll();
        folderRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void storesCardWithTagsAndSm2Defaults() {
        Deck deck = deckRepository.save(new Deck("ITIL", "Kapitel 1", Language.DE));
        Card card = new Card(deck, "Was ist ein Incident?", "Eine ungeplante Unterbrechung.");
        card.setTags(Set.of("ITIL", "Service Operation"));
        cardRepository.saveAndFlush(card);

        entityManager.clear();
        Card reloaded = cardRepository.findById(card.getId()).orElseThrow();

        // Alphabetisch sortiert, nicht in Einfuegereihenfolge.
        assertThat(reloaded.getTags()).containsExactly("ITIL", "Service Operation");
        assertThat(reloaded.getEaseFactor()).isEqualTo(2.5);
        assertThat(reloaded.getDeck().getId()).isEqualTo(deck.getId());
    }

    @Test
    void deletingDeckRemovesItsCardsAndTags() {
        Deck deck = deckRepository.save(new Deck("Wegwerfstapel", null, Language.DE));
        Card card = new Card(deck, "Frage", "Antwort");
        card.setTags(Set.of("tag"));
        cardRepository.saveAndFlush(card);

        // Context leeren, damit wirklich die Kaskadierung der Datenbank greift und
        // nicht Hibernate ueber die noch verwaltete Karte stolpert.
        entityManager.clear();

        deckRepository.deleteById(deck.getId());
        deckRepository.flush();
        entityManager.clear();

        assertThat(cardRepository.findById(card.getId())).isEmpty();
        Integer remainingTags = jdbc.queryForObject(
                "SELECT COUNT(*) FROM card_tags WHERE card_id = ?", Integer.class, card.getId());
        assertThat(remainingTags).isZero();
    }

    @Test
    void deletingSourceDocumentKeepsTheCard() {
        Document document = new Document("skript.pdf", "application/pdf", 100L);
        document.markExtracted("Text");
        documentRepository.saveAndFlush(document);

        Deck deck = deckRepository.save(new Deck("ITIL", null, Language.DE));
        Card card = new Card(deck, "Frage", "Antwort");
        card.setSource(document.getId(), "Originalstelle");
        cardRepository.saveAndFlush(card);
        entityManager.clear();

        documentRepository.deleteById(document.getId());
        documentRepository.flush();
        entityManager.clear();

        UUID sourceAfterDelete = jdbc.queryForObject(
                "SELECT source_document_id FROM cards WHERE id = ?", UUID.class, card.getId());
        assertThat(sourceAfterDelete).isNull();
        assertThat(cardRepository.findById(card.getId())).isPresent();
    }

    @Test
    void countsCardsPerDeckInOneQuery() {
        Deck itil = deckRepository.save(new Deck("ITIL", null, Language.DE));
        Deck leer = deckRepository.save(new Deck("Anatomie", null, Language.DE));
        cardRepository.save(new Card(itil, "Frage 1", "Antwort 1"));
        cardRepository.saveAndFlush(new Card(itil, "Frage 2", "Antwort 2"));

        List<DeckRepository.DeckWithCardCount> decks =
                deckRepository.findAllWithCardCount(Instant.now(), endOfDay());

        // Sortierung nach Name, unabhaengig von Gross- und Kleinschreibung.
        assertThat(decks).extracting(d -> d.getDeck().getName()).containsExactly("Anatomie", "ITIL");
        assertThat(decks).extracting(DeckRepository.DeckWithCardCount::getCardCount)
                .containsExactly(0L, 2L);
        assertThat(leer.getId()).isNotNull();
    }

    @Test
    void countsWhatIsDueTodayAndFindsTheNextDate() {
        Deck deck = deckRepository.save(new Deck("ITIL", null, Language.DE));
        // Frisch angelegte Karten sind sofort faellig.
        cardRepository.save(new Card(deck, "Heute 1", "A"));
        cardRepository.save(new Card(deck, "Heute 2", "B"));

        Instant inDreiTagen = Instant.now().plus(3, ChronoUnit.DAYS);
        Card spaeter = new Card(deck, "Spaeter", "C");
        spaeter.recordReview(2.5, 3, 2, 0, inDreiTagen);
        cardRepository.saveAndFlush(spaeter);

        DeckRepository.DeckWithCardCount result =
                deckRepository.findWithCardCount(deck.getId(), Instant.now(), endOfDay()).orElseThrow();

        assertThat(result.getCardCount()).isEqualTo(3);
        assertThat(result.getDueToday()).isEqualTo(2);
        assertThat(result.getNextDueAt()).isCloseTo(inDreiTagen, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void movesAllCardsToAnotherDeck() {
        Deck ziel = deckRepository.save(new Deck("Ziel", null, Language.DE));
        Deck quelle = deckRepository.save(new Deck("Quelle", null, Language.DE));
        cardRepository.save(new Card(ziel, "Bleibt", "A"));
        cardRepository.save(new Card(quelle, "Wandert 1", "B"));
        cardRepository.saveAndFlush(new Card(quelle, "Wandert 2", "C"));

        assertThat(cardRepository.moveAllToDeck(quelle.getId(), ziel)).isEqualTo(2);

        assertThat(cardRepository.countByDeckId(ziel.getId())).isEqualTo(3);
        assertThat(cardRepository.countByDeckId(quelle.getId())).isZero();
    }

    @Test
    void deletingFolderKeepsItsDecks() {
        Folder folder = folderRepository.saveAndFlush(new Folder("Semester 3"));
        Deck deck = new Deck("ITIL", null, Language.DE);
        deck.moveTo(folder.getId());
        deckRepository.saveAndFlush(deck);
        entityManager.clear();

        folderRepository.deleteById(folder.getId());
        folderRepository.flush();
        entityManager.clear();

        // ON DELETE SET NULL: der Stapel bleibt, nur ohne Ordner.
        assertThat(deckRepository.findById(deck.getId()).orElseThrow().getFolderId()).isNull();
    }

    private Instant endOfDay() {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    @Test
    void listsCardsInCreationOrder() {
        Deck deck = deckRepository.save(new Deck("ITIL", null, Language.DE));
        cardRepository.saveAndFlush(new Card(deck, "Zuerst", "A"));
        cardRepository.saveAndFlush(new Card(deck, "Danach", "B"));

        assertThat(cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()))
                .extracting(Card::getFront)
                .containsExactly("Zuerst", "Danach");
    }
}
