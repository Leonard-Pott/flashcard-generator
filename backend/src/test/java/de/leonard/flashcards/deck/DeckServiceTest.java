package de.leonard.flashcards.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckDtos.UpdateDeckRequest;

@ExtendWith(MockitoExtension.class)
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private FolderRepository folderRepository;

    private DeckService service;

    @BeforeEach
    void setUp() {
        service = new DeckService(deckRepository, cardRepository, folderRepository,
                Clock.systemDefaultZone());
        lenient().when(deckRepository.save(any(Deck.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsDeckWithGermanAsDefaultLanguage() {
        Deck deck = service.create(new CreateDeckRequest("  ITIL Service Design  ", "  ", null, null));

        assertThat(deck.getName()).isEqualTo("ITIL Service Design");
        assertThat(deck.getLanguage()).isEqualTo(Language.DE);
        // Eine Beschreibung aus Leerzeichen ist keine Beschreibung.
        assertThat(deck.getDescription()).isNull();
    }

    @Test
    void createsDeckWithExplicitLanguage() {
        Deck deck = service.create(new CreateDeckRequest("Vocabulary", "Chapter 1", Language.EN, null));

        assertThat(deck.getLanguage()).isEqualTo(Language.EN);
        assertThat(deck.getDescription()).isEqualTo("Chapter 1");
    }

    @Test
    void updateChangesOnlyTheGivenFields() {
        Deck deck = existingDeck("Alt", "Beschreibung", Language.DE);

        Deck updated = service.update(deck.getId(), new UpdateDeckRequest("Neu", null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Neu");
        assertThat(updated.getDescription()).isEqualTo("Beschreibung");
        assertThat(updated.getLanguage()).isEqualTo(Language.DE);
    }

    @Test
    void updateClearsDescriptionWhenEmptyStringIsSent() {
        Deck deck = existingDeck("Stapel", "Beschreibung", Language.DE);

        Deck updated = service.update(deck.getId(), new UpdateDeckRequest(null, "", null, null, null));

        assertThat(updated.getDescription()).isNull();
    }

    @Test
    void updateRejectsBlankName() {
        Deck deck = existingDeck("Stapel", null, Language.DE);

        assertThatThrownBy(() -> service.update(deck.getId(), new UpdateDeckRequest("   ", null, null, null, null)))
                .isInstanceOf(InvalidDeckException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void updateBumpsUpdatedAtButKeepsCreatedAt() throws InterruptedException {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        var updatedBefore = deck.getUpdatedAt();
        var createdAt = deck.getCreatedAt();
        Thread.sleep(5);

        service.update(deck.getId(), new UpdateDeckRequest("Neuer Name", null, null, null, null));

        assertThat(deck.getUpdatedAt()).isAfter(updatedBefore);
        assertThat(deck.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateFailsForUnknownDeck() {
        UUID id = UUID.randomUUID();
        given(deckRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateDeckRequest("Neu", null, null, null, null)))
                .isInstanceOf(DeckNotFoundException.class);
    }

    @Test
    void updateSetsAndClearsTheExamDate() {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        LocalDate exam = LocalDate.of(2026, 8, 14);

        assertThat(service.update(deck.getId(), new UpdateDeckRequest(null, null, null, "2026-08-14", null))
                .getExamDate()).isEqualTo(exam);

        // Ein fehlendes Feld laesst den Termin stehen ...
        assertThat(service.update(deck.getId(), new UpdateDeckRequest("Anders", null, null, null, null))
                .getExamDate()).isEqualTo(exam);

        // ... ein leerer String entfernt ihn.
        assertThat(service.update(deck.getId(), new UpdateDeckRequest(null, null, null, "", null))
                .getExamDate()).isNull();
    }

    @Test
    void updateRejectsAnUnparsableExamDate() {
        Deck deck = existingDeck("Stapel", null, Language.DE);

        assertThatThrownBy(() -> service.update(deck.getId(),
                new UpdateDeckRequest(null, null, null, "irgendwann", null)))
                .isInstanceOf(InvalidDeckException.class)
                .hasMessageContaining("Datum");
    }

    @Test
    void updateMovesDeckIntoFolderAndOutAgain() {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        UUID folderId = UUID.randomUUID();
        given(folderRepository.existsById(folderId)).willReturn(true);

        assertThat(service.update(deck.getId(), new UpdateDeckRequest(null, null, null, null, folderId.toString()))
                .getFolderId()).isEqualTo(folderId);

        // Ein fehlendes Feld laesst die Zuordnung stehen ...
        assertThat(service.update(deck.getId(), new UpdateDeckRequest("Anders", null, null, null, null))
                .getFolderId()).isEqualTo(folderId);

        // ... ein leerer String nimmt den Stapel heraus.
        assertThat(service.update(deck.getId(), new UpdateDeckRequest(null, null, null, null, ""))
                .getFolderId()).isNull();
    }

    /** Der Fehler, der den Anlass gab: ein Ordnerwechsel loeschte den Pruefungstermin. */
    @Test
    void movingIntoAFolderKeepsTheExamDate() {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        UUID folderId = UUID.randomUUID();
        given(folderRepository.existsById(folderId)).willReturn(true);
        deck.examOn(LocalDate.of(2026, 8, 14));

        Deck moved = service.update(deck.getId(), new UpdateDeckRequest(null, null, null, null, folderId.toString()));

        assertThat(moved.getFolderId()).isEqualTo(folderId);
        assertThat(moved.getExamDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void updateRejectsUnknownFolder() {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        UUID folderId = UUID.randomUUID();
        given(folderRepository.existsById(folderId)).willReturn(false);

        assertThatThrownBy(() -> service.update(deck.getId(),
                new UpdateDeckRequest(null, null, null, null, folderId.toString())))
                .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    void mergeMovesCardsAndDropsTheEmptiedDeck() {
        Deck target = existingDeck("Ziel", null, Language.DE);
        Deck source = existingDeck("Quelle", null, Language.DE);

        assertThat(service.merge(target.getId(), source.getId())).isEqualTo(target);

        verify(cardRepository).moveAllToDeck(source.getId(), target);
        verify(deckRepository).deleteById(source.getId());
    }

    @Test
    void mergeRejectsTheSameDeckTwice() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.merge(id, id)).isInstanceOf(InvalidDeckException.class);
        verify(cardRepository, never()).moveAllToDeck(any(), any());
    }

    @Test
    void deleteFailsForUnknownDeck() {
        UUID id = UUID.randomUUID();
        given(deckRepository.existsById(id)).willReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DeckNotFoundException.class);
        verify(deckRepository, never()).deleteById(id);
    }

    private Deck existingDeck(String name, String description, Language language) {
        Deck deck = new Deck(name, description, language);
        given(deckRepository.findById(deck.getId())).willReturn(Optional.of(deck));
        return deck;
    }
}

