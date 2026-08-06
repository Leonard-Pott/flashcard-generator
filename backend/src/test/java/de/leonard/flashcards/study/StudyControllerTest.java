package de.leonard.flashcards.study;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;
import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardNotFoundException;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckNotFoundException;
import de.leonard.flashcards.deck.Language;

@WebMvcTest(StudyController.class)
@Import(ApiExceptionHandler.class)
class StudyControllerTest {

    private static final Deck DECK = new Deck("ITIL", null, Language.DE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyService service;

    @Test
    void nextReturnsTheDueCard() throws Exception {
        Card card = new Card(DECK, "Was ist ein Incident?", "Eine Unterbrechung.");
        given(service.nextDue(DECK.getId()))
                .willReturn(new StudyService.NextCard(card, 5, null));

        mockMvc.perform(get("/api/decks/{deckId}/study/next", DECK.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.front").value("Was ist ein Incident?"))
                .andExpect(jsonPath("$.card.back").value("Eine Unterbrechung."))
                .andExpect(jsonPath("$.dueCount").value(5))
                .andExpect(jsonPath("$.nextDueAt").doesNotExist());
    }

    @Test
    void nextReturnsNoCardButTheFollowingDueTime() throws Exception {
        // Zehn Minuten entfernt: genau der Fall, den ein reines Datum nicht ausdruecken kann.
        given(service.nextDue(DECK.getId()))
                .willReturn(new StudyService.NextCard(null, 0, Instant.parse("2026-07-30T18:40:00Z")));

        mockMvc.perform(get("/api/decks/{deckId}/study/next", DECK.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card").doesNotExist())
                .andExpect(jsonPath("$.dueCount").value(0))
                .andExpect(jsonPath("$.nextDueAt").value("2026-07-30T18:40:00Z"));
    }

    @Test
    void nextOfUnknownDeckReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new DeckNotFoundException(id)).given(service).nextDue(id);

        mockMvc.perform(get("/api/decks/{deckId}/study/next", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Stapel nicht gefunden: " + id));
    }

    @Test
    void reviewReturnsTheUpdatedCard() throws Exception {
        Card card = new Card(DECK, "Frage", "Antwort");
        card.recordReview(2.6, 6, 2, Sm2.GRADUATED, Instant.parse("2026-08-02T00:00:00Z"));
        given(service.review(eq(card.getId()), anyInt())).willReturn(card);

        mockMvc.perform(post("/api/cards/{cardId}/review", card.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quality\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.easeFactor").value(2.6))
                .andExpect(jsonPath("$.intervalDays").value(6))
                .andExpect(jsonPath("$.repetitions").value(2))
                .andExpect(jsonPath("$.dueAt").value("2026-08-02T00:00:00Z"));

        verify(service).review(card.getId(), 5);
    }

    @Test
    void reviewRejectsQualityAboveFive() throws Exception {
        mockMvc.perform(post("/api/cards/{cardId}/review", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quality\": 6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Bewertung muss zwischen 0 und 5 liegen."));

        verify(service, never()).review(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void reviewRejectsMissingQuality() throws Exception {
        mockMvc.perform(post("/api/cards/{cardId}/review", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Es muss eine Bewertung angegeben werden."));
    }

    @Test
    void reviewOfUnknownCardReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new CardNotFoundException(id)).given(service).review(eq(id), anyInt());

        mockMvc.perform(post("/api/cards/{cardId}/review", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quality\": 3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Karte nicht gefunden: " + id));
    }
}
