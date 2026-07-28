package de.leonard.flashcards.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckNotFoundException;
import de.leonard.flashcards.deck.DeckService;
import de.leonard.flashcards.deck.Language;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private DeckService deckService;

    @Mock
    private CardRepository cardRepository;

    private ExportService service;

    @BeforeEach
    void setUp() {
        service = new ExportService(deckService, cardRepository,
                List.of(new CsvDeckExportWriter(), new AnkiDeckExportWriter()));
    }

    @Test
    void csvExportUsesTheDeckNameAsFilename() {
        Deck deck = givenDeck("ITIL Service Design");

        ExportService.Export export = service.export(deck.getId(), ExportFormat.CSV);

        assertThat(export.filename()).isEqualTo("ITIL-Service-Design.csv");
        assertThat(export.contentType()).isEqualTo("text/csv;charset=UTF-8");
    }

    @Test
    void ankiExportUsesTxtExtension() {
        Deck deck = givenDeck("ITIL");

        ExportService.Export export = service.export(deck.getId(), ExportFormat.ANKI);

        assertThat(export.filename()).isEqualTo("ITIL.txt");
        assertThat(export.contentType()).isEqualTo("text/tab-separated-values;charset=UTF-8");
    }

    @Test
    void resolvesUmlautsAndSpecialCharactersInTheFilename() {
        Deck deck = givenDeck("Prüfung: Anatomie & Physiologie (2026)");

        assertThat(service.export(deck.getId(), ExportFormat.CSV).filename())
                .isEqualTo("Prufung-Anatomie-Physiologie-2026.csv");
    }

    @Test
    void fallsBackWhenTheNameHasNoUsableCharacters() {
        Deck deck = givenDeck("+++");

        assertThat(service.export(deck.getId(), ExportFormat.CSV).filename()).isEqualTo("stapel.csv");
    }

    @Test
    void shortensVeryLongNames() {
        Deck deck = givenDeck("Sehr langer Stapelname ".repeat(20));

        String filename = service.export(deck.getId(), ExportFormat.CSV).filename();

        assertThat(filename).hasSizeLessThanOrEqualTo(84);
        assertThat(filename).endsWith(".csv");
    }

    @Test
    void exportsTheCardsOfTheDeck() {
        Deck deck = givenDeck("ITIL");
        given(cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()))
                .willReturn(List.of(new Card(deck, "Frage", "Antwort")));

        assertThat(new String(service.export(deck.getId(), ExportFormat.ANKI).content()))
                .isEqualTo("Frage\tAntwort\t\n");
    }

    @Test
    void failsForUnknownDeck() {
        UUID unknown = UUID.randomUUID();
        given(deckService.findById(unknown)).willThrow(new DeckNotFoundException(unknown));

        assertThatThrownBy(() -> service.export(unknown, ExportFormat.CSV))
                .isInstanceOf(DeckNotFoundException.class);
    }

    private Deck givenDeck(String name) {
        Deck deck = new Deck(name, null, Language.DE);
        given(deckService.findById(deck.getId())).willReturn(deck);
        lenient().when(cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()))
                .thenReturn(List.of());
        return deck;
    }
}
