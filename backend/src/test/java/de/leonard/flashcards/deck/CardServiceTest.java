package de.leonard.flashcards.deck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.leonard.flashcards.deck.CardDtos.CreateCardRequest;
import de.leonard.flashcards.deck.CardDtos.UpdateCardRequest;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private DeckService deckService;

    private CardService service;
    private Deck deck;

    @BeforeEach
    void setUp() {
        service = new CardService(cardRepository, deckService);
        deck = new Deck("ITIL", null, Language.DE);
        lenient().when(deckService.findById(deck.getId())).thenReturn(deck);
        lenient().when(cardRepository.save(any(Card.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void newCardStartsWithSm2Defaults() {
        Card card = service.create(deck.getId(), new CreateCardRequest("Frage", "Antwort", null, null, null));

        assertThat(card.getEaseFactor()).isEqualTo(2.5);
        assertThat(card.getIntervalDays()).isZero();
        assertThat(card.getRepetitions()).isZero();
        // Neue Karten stehen auf dem ersten Lernschritt und sind sofort lernbar.
        assertThat(card.getLearningStep()).isZero();
        assertThat(card.getDueAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void trimsFrontAndBack() {
        Card card = service.create(
                deck.getId(), new CreateCardRequest("  Frage  ", "  Antwort  ", null, null, null));

        assertThat(card.getFront()).isEqualTo("Frage");
        assertThat(card.getBack()).isEqualTo("Antwort");
    }

    @Test
    void keepsSourceForTraceability() {
        UUID documentId = UUID.randomUUID();

        Card card = service.create(
                deck.getId(),
                new CreateCardRequest("Frage", "Antwort", null, documentId, "Originaltext aus dem Skript"));

        assertThat(card.getSourceDocumentId()).isEqualTo(documentId);
        assertThat(card.getSourceExcerpt()).isEqualTo("Originaltext aus dem Skript");
    }

    @Test
    void dropsBlankTagsTrimsAndSortsTheRest() {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("  Service Design  ");
        tags.add("   ");
        tags.add("ITIL");

        Card card = service.create(
                deck.getId(), new CreateCardRequest("Frage", "Antwort", tags, null, null));

        // Sortiert, damit die POST-Antwort zur spaeteren GET-Antwort passt.
        assertThat(card.getTags()).containsExactly("ITIL", "Service Design");
    }

    @Test
    void createFailsForUnknownDeck() {
        UUID unknown = UUID.randomUUID();
        given(deckService.findById(unknown)).willThrow(new DeckNotFoundException(unknown));

        assertThatThrownBy(() ->
                service.create(unknown, new CreateCardRequest("Frage", "Antwort", null, null, null)))
                .isInstanceOf(DeckNotFoundException.class);
        verify(cardRepository, never()).save(any());
    }

    @Test
    void updateChangesOnlyTheGivenFields() {
        Card card = existingCard("Frage", "Antwort");

        Card updated = service.update(card.getId(), new UpdateCardRequest(null, "Neue Antwort", null));

        assertThat(updated.getFront()).isEqualTo("Frage");
        assertThat(updated.getBack()).isEqualTo("Neue Antwort");
    }

    @Test
    void updateRejectsBlankFront() {
        Card card = existingCard("Frage", "Antwort");

        assertThatThrownBy(() -> service.update(card.getId(), new UpdateCardRequest("  ", null, null)))
                .isInstanceOf(InvalidDeckException.class)
                .hasMessageContaining("Vorderseite");
    }

    @Test
    void updateReplacesTagsCompletely() {
        Card card = existingCard("Frage", "Antwort");
        card.setTags(Set.of("alt"));

        Card updated = service.update(card.getId(), new UpdateCardRequest(null, null, Set.of("neu")));

        assertThat(updated.getTags()).containsExactly("neu");
    }

    @Test
    void listingCardsOfAnUnknownDeckFails() {
        UUID unknown = UUID.randomUUID();
        given(deckService.findById(unknown)).willThrow(new DeckNotFoundException(unknown));

        assertThatThrownBy(() -> service.findByDeck(unknown)).isInstanceOf(DeckNotFoundException.class);
    }

    @Test
    void deleteFailsForUnknownCard() {
        UUID id = UUID.randomUUID();
        given(cardRepository.existsById(id)).willReturn(false);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(CardNotFoundException.class);
        verify(cardRepository, never()).deleteById(id);
    }

    private Card existingCard(String front, String back) {
        Card card = new Card(deck, front, back);
        given(cardRepository.findById(card.getId())).willReturn(Optional.of(card));
        return card;
    }
}
