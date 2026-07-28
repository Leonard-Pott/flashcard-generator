package de.leonard.flashcards.export;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.leonard.flashcards.config.ApiExceptionHandler;
import de.leonard.flashcards.deck.DeckNotFoundException;

@WebMvcTest(ExportController.class)
@Import(ApiExceptionHandler.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService service;

    @Test
    void exportsCsvAsAttachment() throws Exception {
        UUID deckId = UUID.randomUUID();
        given(service.export(deckId, ExportFormat.CSV)).willReturn(new ExportService.Export(
                "ITIL.csv", "text/csv;charset=UTF-8", "Vorderseite;Rueckseite\r\n".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/decks/{id}/export?format=csv", deckId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ITIL.csv\""))
                .andExpect(content().string("Vorderseite;Rueckseite\r\n"));
    }

    @Test
    void usesCsvWhenNoFormatIsGiven() throws Exception {
        UUID deckId = UUID.randomUUID();
        given(service.export(deckId, ExportFormat.CSV))
                .willReturn(new ExportService.Export("ITIL.csv", "text/csv;charset=UTF-8", new byte[0]));

        mockMvc.perform(get("/api/decks/{id}/export", deckId)).andExpect(status().isOk());
    }

    @Test
    void exportsAnkiTsv() throws Exception {
        UUID deckId = UUID.randomUUID();
        given(service.export(deckId, ExportFormat.ANKI)).willReturn(new ExportService.Export(
                "ITIL.txt", "text/tab-separated-values;charset=UTF-8",
                "Frage\tAntwort\t\n".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/decks/{id}/export?format=anki", deckId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ITIL.txt\""))
                .andExpect(content().string("Frage\tAntwort\t\n"));
    }

    @Test
    void unknownFormatReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/decks/{id}/export?format=pdf", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Unbekanntes Exportformat: pdf. Moeglich sind csv und anki."));
    }

    @Test
    void unknownDeckReturnsNotFound() throws Exception {
        UUID deckId = UUID.randomUUID();
        willThrow(new DeckNotFoundException(deckId)).given(service).export(deckId, ExportFormat.CSV);

        mockMvc.perform(get("/api/decks/{id}/export", deckId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Stapel nicht gefunden: " + deckId));
    }
}
