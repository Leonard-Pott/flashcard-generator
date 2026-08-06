package de.leonard.flashcards.deck;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;

@WebMvcTest(CardController.class)
@Import(ApiExceptionHandler.class)
class CardControllerTest {

    private static final Deck DECK = new Deck("ITIL", null, Language.DE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardService service;

    @Test
    void listReturnsCardsOfDeck() throws Exception {
        Card card = new Card(DECK, "Was ist ein Incident?", "Eine ungeplante Unterbrechung.");
        card.setTags(Set.of("ITIL"));
        given(service.findByDeck(DECK.getId())).willReturn(List.of(card));

        mockMvc.perform(get("/api/decks/{deckId}/cards", DECK.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].front").value("Was ist ein Incident?"))
                .andExpect(jsonPath("$[0].deckId").value(DECK.getId().toString()))
                .andExpect(jsonPath("$[0].tags[0]").value("ITIL"))
                .andExpect(jsonPath("$[0].easeFactor").value(2.5))
                .andExpect(jsonPath("$[0].dueAt").exists());
    }

    @Test
    void listOfUnknownDeckReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new DeckNotFoundException(id)).given(service).findByDeck(id);

        mockMvc.perform(get("/api/decks/{deckId}/cards", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Stapel nicht gefunden: " + id));
    }

    @Test
    void createReturnsCreatedWithLocationOnCardResource() throws Exception {
        Card card = new Card(DECK, "Frage", "Antwort");
        given(service.create(eq(DECK.getId()), any())).willReturn(card);

        mockMvc.perform(post("/api/decks/{deckId}/cards", DECK.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front": "Frage", "back": "Antwort"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/cards/" + card.getId()))
                .andExpect(jsonPath("$.front").value("Frage"));
    }

    @Test
    void createRejectsEmptyFront() throws Exception {
        mockMvc.perform(post("/api/decks/{deckId}/cards", DECK.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front": "", "back": "Antwort"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Vorderseite darf nicht leer sein."));
    }

    @Test
    void patchUpdatesCard() throws Exception {
        Card card = new Card(DECK, "Frage", "Korrigierte Antwort");
        given(service.update(eq(card.getId()), any())).willReturn(card);

        mockMvc.perform(patch("/api/cards/{id}", card.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"back": "Korrigierte Antwort"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.back").value("Korrigierte Antwort"));
    }

    @Test
    void patchWithBlankFrontReturnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new InvalidDeckException("Die Vorderseite darf nicht leer sein."))
                .given(service).update(eq(id), any());

        mockMvc.perform(patch("/api/cards/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"front": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Die Vorderseite darf nicht leer sein."));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/cards/{id}", id)).andExpect(status().isNoContent());

        verify(service).delete(id);
    }
}
