package de.leonard.flashcards.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private DeckService service;

    @BeforeEach
    void setUp() {
        service = new DeckService(deckRepository, cardRepository);
        lenient().when(deckRepository.save(any(Deck.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsDeckWithGermanAsDefaultLanguage() {
        Deck deck = service.create(new CreateDeckRequest("  ITIL Service Design  ", "  ", null));

        assertThat(deck.getName()).isEqualTo("ITIL Service Design");
        assertThat(deck.getLanguage()).isEqualTo(Language.DE);
        // Eine Beschreibung aus Leerzeichen ist keine Beschreibung.
        assertThat(deck.getDescription()).isNull();
    }

    @Test
    void createsDeckWithExplicitLanguage() {
        Deck deck = service.create(new CreateDeckRequest("Vocabulary", "Chapter 1", Language.EN));

        assertThat(deck.getLanguage()).isEqualTo(Language.EN);
        assertThat(deck.getDescription()).isEqualTo("Chapter 1");
    }

    @Test
    void updateChangesOnlyTheGivenFields() {
        Deck deck = existingDeck("Alt", "Beschreibung", Language.DE);

        Deck updated = service.update(deck.getId(), new UpdateDeckRequest("Neu", null, null));

        assertThat(updated.getName()).isEqualTo("Neu");
        assertThat(updated.getDescription()).isEqualTo("Beschreibung");
        assertThat(updated.getLanguage()).isEqualTo(Language.DE);
    }

    @Test
    void updateClearsDescriptionWhenEmptyStringIsSent() {
        Deck deck = existingDeck("Stapel", "Beschreibung", Language.DE);

        Deck updated = service.update(deck.getId(), new UpdateDeckRequest(null, "", null));

        assertThat(updated.getDescription()).isNull();
    }

    @Test
    void updateRejectsBlankName() {
        Deck deck = existingDeck("Stapel", null, Language.DE);

        assertThatThrownBy(() -> service.update(deck.getId(), new UpdateDeckRequest("   ", null, null)))
                .isInstanceOf(InvalidDeckException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void updateBumpsUpdatedAtButKeepsCreatedAt() throws InterruptedException {
        Deck deck = existingDeck("Stapel", null, Language.DE);
        var updatedBefore = deck.getUpdatedAt();
        var createdAt = deck.getCreatedAt();
        Thread.sleep(5);

        service.update(deck.getId(), new UpdateDeckRequest("Neuer Name", null, null));

        assertThat(deck.getUpdatedAt()).isAfter(updatedBefore);
        assertThat(deck.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void updateFailsForUnknownDeck() {
        UUID id = UUID.randomUUID();
        given(deckRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateDeckRequest("Neu", null, null)))
                .isInstanceOf(DeckNotFoundException.class);
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
