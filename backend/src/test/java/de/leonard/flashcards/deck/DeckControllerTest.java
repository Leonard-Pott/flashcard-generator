package de.leonard.flashcards.deck;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;
import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckDtos.UpdateDeckRequest;
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
        Instant morgen = Instant.parse("2026-08-07T06:00:00Z");
        given(service.findAll()).willReturn(List.of(
                projection(new Deck("ITIL", "Kapitel 1-3", Language.DE), 42, 5, morgen),
                projection(new Deck("Vocabulary", null, Language.EN), 0)));

        mockMvc.perform(get("/api/decks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ITIL"))
                .andExpect(jsonPath("$[0].language").value("de"))
                .andExpect(jsonPath("$[0].cardCount").value(42))
                .andExpect(jsonPath("$[0].dueToday").value(5))
                .andExpect(jsonPath("$[0].nextDueAt").value("2026-08-07T06:00:00Z"))
                .andExpect(jsonPath("$[1].language").value("en"))
                .andExpect(jsonPath("$[1].cardCount").value(0))
                .andExpect(jsonPath("$[1].dueToday").value(0))
                .andExpect(jsonPath("$[1].nextDueAt").doesNotExist());
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
        given(service.findWithCardCount(deck.getId())).willReturn(projection(deck, 7));

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

    /**
     * Ein Teil-PATCH darf nur anfassen, was mitgeschickt wurde. Der Test laeuft bewusst
     * ueber die JSON-Bindung - genau dort ging der Pruefungstermin frueher verloren.
     */
    @Test
    void patchLeavesFieldsThatWereNotSentAlone() throws Exception {
        Deck deck = new Deck("Stapel", null, Language.DE);
        given(service.update(eq(deck.getId()), any())).willReturn(deck);
        given(service.findWithCardCount(deck.getId())).willReturn(projection(deck, 0));

        mockMvc.perform(patch("/api/decks/{id}", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Nur der Name"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateDeckRequest> request = ArgumentCaptor.forClass(UpdateDeckRequest.class);
        verify(service).update(eq(deck.getId()), request.capture());
        assertThat(request.getValue().name()).isEqualTo("Nur der Name");
        assertThat(request.getValue().examDate()).isNull();
        assertThat(request.getValue().folderId()).isNull();
    }

    @Test
    void patchClearsFieldsSentAsEmptyString() throws Exception {
        Deck deck = new Deck("Stapel", null, Language.DE);
        given(service.update(eq(deck.getId()), any())).willReturn(deck);
        given(service.findWithCardCount(deck.getId())).willReturn(projection(deck, 0));

        mockMvc.perform(patch("/api/decks/{id}", deck.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"examDate": "", "folderId": ""}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateDeckRequest> request = ArgumentCaptor.forClass(UpdateDeckRequest.class);
        verify(service).update(eq(deck.getId()), request.capture());
        assertThat(request.getValue().examDate()).isEmpty();
        assertThat(request.getValue().folderId()).isEmpty();
    }

    @Test
    void mergeReturnsTheTargetDeck() throws Exception {
        Deck target = new Deck("Ziel", null, Language.DE);
        UUID sourceId = UUID.randomUUID();
        given(service.findWithCardCount(target.getId())).willReturn(projection(target, 12));

        mockMvc.perform(post("/api/decks/{id}/merge", target.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceDeckId": "%s"}
                                """.formatted(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ziel"))
                .andExpect(jsonPath("$.cardCount").value(12));

        verify(service).merge(target.getId(), sourceId);
    }

    @Test
    void mergeRequiresASourceDeck() throws Exception {
        mockMvc.perform(post("/api/decks/{id}/merge", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Es muss ein Quellstapel angegeben werden."));
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
        return projection(deck, cardCount, 0, null);
    }

    private DeckWithCardCount projection(Deck deck, long cardCount, long dueToday, Instant nextDueAt) {
        return new DeckWithCardCount() {
            @Override
            public Deck getDeck() {
                return deck;
            }

            @Override
            public long getCardCount() {
                return cardCount;
            }

            @Override
            public long getDueToday() {
                return dueToday;
            }

            @Override
            public Instant getNextDueAt() {
                return nextDueAt;
            }
        };
    }
}

