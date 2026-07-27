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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;
import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckRepository.DeckWithCardCount;

@WebMvcTest(DeckController.class)
@Import(ApiExceptionHandler.class)
class DeckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeckService service;

    @Test
    void listReturnsDecksWithCardCount() throws Exception {
        given(service.findAll()).willReturn(List.of(
                projection(new Deck("ITIL", "Kapitel 1-3", Language.DE), 42),
                projection(new Deck("Vocabulary", null, Language.EN), 0)));

        mockMvc.perform(get("/api/decks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ITIL"))
                .andExpect(jsonPath("$[0].language").value("de"))
                .andExpect(jsonPath("$[0].cardCount").value(42))
                .andExpect(jsonPath("$[1].language").value("en"))
                .andExpect(jsonPath("$[1].cardCount").value(0));
    }

    @Test
    void createReturnsCreatedWithLocation() throws Exception {
        Deck deck = new Deck("ITIL Service Design", null, Language.DE);
        given(service.create(any(CreateDeckRequest.class))).willReturn(deck);

        mockMvc.perform(post("/api/decks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "ITIL Service Design"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/decks/" + deck.getId()))
                .andExpect(jsonPath("$.name").value("ITIL Service Design"))
                .andExpect(jsonPath("$.cardCount").value(0));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/decks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Der Name darf nicht leer sein."));
    }

    @Test
    void createRejectsUnknownLanguage() throws Exception {
        mockMvc.perform(post("/api/decks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Stapel", "language": "fr"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unbekannte Sprache: fr"));
    }

    @Test
    void patchUpdatesDeck() throws Exception {
        Deck deck = new Deck("Neuer Name", null, Language.EN);
        given(service.update(eq(deck.getId()), any())).willReturn(deck);
        given(service.countCards(deck.getId())).willReturn(7L);

        mockMvc.perform(patch("/api/decks/{id}", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Neuer Name", "language": "en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Neuer Name"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.cardCount").value(7));
    }

    @Test
    void getOfUnknownDeckReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new DeckNotFoundException(id)).given(service).findWithCardCount(id);

        mockMvc.perform(get("/api/decks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Stapel nicht gefunden: " + id));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/decks/{id}", id)).andExpect(status().isNoContent());

        verify(service).delete(id);
    }

    private DeckWithCardCount projection(Deck deck, long cardCount) {
        return new DeckWithCardCount() {
            @Override
            public Deck getDeck() {
                return deck;
            }

            @Override
            public long getCardCount() {
                return cardCount;
            }
        };
    }
}
