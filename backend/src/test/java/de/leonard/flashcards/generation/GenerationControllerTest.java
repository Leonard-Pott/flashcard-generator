package de.leonard.flashcards.generation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import de.leonard.flashcards.deck.Language;
import de.leonard.flashcards.generation.GenerationDtos.GenerationRequest;

@WebMvcTest(GenerationController.class)
@Import(ApiExceptionHandler.class)
class GenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerationService service;

    @MockitoBean
    private GenerationRunner runner;

    @MockitoBean
    private de.leonard.flashcards.llm.LlmClient llmClient;

    @Test
    void budgetReportsTheLimitsOfTheTariff() throws Exception {
        given(llmClient.modelName()).willReturn("gemini-3.6-flash");
        given(llmClient.dailyRequestBudget()).willReturn(20);
        given(llmClient.requestsPerMinute()).willReturn(10);

        mockMvc.perform(get("/api/generation-jobs/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("gemini-3.6-flash"))
                .andExpect(jsonPath("$.dailyRequestBudget").value(20))
                .andExpect(jsonPath("$.requestsPerMinute").value(10));
    }

    @Test
    void createReturnsAcceptedAndStartsTheRun() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID deckId = UUID.randomUUID();
        GenerationJob job = new GenerationJob(
                List.of(documentId), deckId, Language.DE, CardStyle.QA, 5);
        given(service.createJob(any(GenerationRequest.class))).willReturn(job);

        mockMvc.perform(post("/api/generation-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIds": ["%s"], "deckId": "%s", "cardsPerChunk": 5, "style": "QA"}
                                """.formatted(documentId, deckId)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/generation-jobs/" + job.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetDeckId").value(deckId.toString()))
                .andExpect(jsonPath("$.processedChunks").value(0));

        verify(runner).run(job.getId());
    }

    @Test
    void createWithoutDocumentsIsRejected() throws Exception {
        mockMvc.perform(post("/api/generation-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIds": [], "newDeckName": "Neu"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Es muss mindestens ein Dokument ausgewaehlt sein."));

        verify(runner, never()).run(any());
    }

    @Test
    void createWithTooManyCardsPerChunkIsRejected() throws Exception {
        mockMvc.perform(post("/api/generation-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIds": ["%s"], "newDeckName": "Neu", "cardsPerChunk": 50}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("cardsPerChunk muss zwischen 1 und 20 liegen."));
    }

    @Test
    void createWithFailedDocumentReturnsBadRequest() throws Exception {
        willThrow(new InvalidGenerationRequestException(
                "Aus diesen Dokumenten wurde kein Text extrahiert: scan.pdf"))
                .given(service).createJob(any());

        mockMvc.perform(post("/api/generation-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIds": ["%s"], "newDeckName": "Neu"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Aus diesen Dokumenten wurde kein Text extrahiert: scan.pdf"));

        verify(runner, never()).run(any());
    }

    @Test
    void getReturnsProgress() throws Exception {
        GenerationJob job = new GenerationJob(
                List.of(UUID.randomUUID()), UUID.randomUUID(), Language.DE, CardStyle.QA, 5);
        job.start(12, "gemini-3.6-flash");
        job.recordProcessedChunk(4);
        given(service.findById(job.getId())).willReturn(job);

        mockMvc.perform(get("/api/generation-jobs/{id}", job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.totalChunks").value(12))
                .andExpect(jsonPath("$.processedChunks").value(1))
                .andExpect(jsonPath("$.generatedCardCount").value(4))
                .andExpect(jsonPath("$.llmModel").value("gemini-3.6-flash"));
    }

    @Test
    void getOfUnknownJobReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new GenerationJobNotFoundException(id)).given(service).findById(id);

        mockMvc.perform(get("/api/generation-jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Generierungslauf nicht gefunden: " + id));
    }
}
