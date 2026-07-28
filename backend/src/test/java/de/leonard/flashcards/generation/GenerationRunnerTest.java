package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.leonard.flashcards.deck.Language;
import de.leonard.flashcards.document.Document;
import de.leonard.flashcards.document.DocumentRepository;
import de.leonard.flashcards.llm.LlmClient;
import de.leonard.flashcards.llm.LlmException;

@ExtendWith(MockitoExtension.class)
class GenerationRunnerTest {

    private static final String SENTENCE = "Ein Satz ueber Service Management. ";

    @Mock
    private GenerationJobStore store;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private LlmClient llmClient;

    private final TextChunker chunker = new TextChunker();

    private GenerationRunner runner;
    private UUID jobId;
    private UUID deckId;
    private UUID documentId;
    /** Wie viele Abschnitte der Text tatsaechlich ergibt - nicht geraten, sondern gerechnet. */
    private int expectedChunks;

    @BeforeEach
    void setUp() {
        runner = new GenerationRunner(store, documentRepository, chunker,
                new CardPromptFactory(), new CardJsonParser(new ObjectMapper()),
                new CardDeduplicator(), llmClient);

        jobId = UUID.randomUUID();
        deckId = UUID.randomUUID();

        lenient().when(llmClient.modelName()).thenReturn("gemini-3.6-flash");
        lenient().when(store.existingFronts(deckId)).thenReturn(Set.of());
        lenient().when(store.saveCards(any(), any(), any(), any()))
                .thenAnswer(call -> ((List<?>) call.getArgument(1)).size());
    }

    @Test
    void savesCardsAndCompletesTheJob() {
        givenJobWithText(oneChunk());
        given(llmClient.complete(anyString(), anyString())).willReturn("""
                [{"front": "Was ist ein Incident?", "back": "Eine Unterbrechung.", "tags": ["ITIL"]}]""");

        runner.run(jobId);

        verify(store).markRunning(jobId, 1, "gemini-3.6-flash");
        verify(store).recordProcessedChunk(jobId, 1);
        verify(store).complete(jobId, null);
        verify(store, never()).fail(any(), any());
    }

    @Test
    void passesSourceDocumentAndExcerptToTheCards() {
        String text = oneChunk();
        givenJobWithText(text);
        given(llmClient.complete(anyString(), anyString()))
                .willReturn("[{\"front\": \"A\", \"back\": \"B\"}]");

        runner.run(jobId);

        ArgumentCaptor<UUID> source = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> chunk = ArgumentCaptor.forClass(String.class);
        verify(store).saveCards(eq(deckId), any(), source.capture(), chunk.capture());
        assertThat(source.getValue()).isEqualTo(documentId);
        assertThat(text).contains(chunk.getValue().substring(0, 50));
    }

    @Test
    void skipsFailedChunkAndKeepsGoing() {
        givenJobWithText(severalChunks());
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new LlmException("Gemini antwortete auch nach 5 Versuchen mit HTTP 429"))
                .willReturn("[{\"front\": \"A\", \"back\": \"B\"}]");

        runner.run(jobId);

        verify(store).recordSkippedChunk(jobId);
        verify(store, times(expectedChunks - 1)).recordProcessedChunk(eq(jobId), anyInt());

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(store).complete(eq(jobId), note.capture());
        assertThat(note.getValue())
                .contains("1 von " + expectedChunks)
                .contains("429");
    }

    @Test
    void statesTheSameCauseOnlyOnce() {
        givenJobWithText(severalChunks());
        // Ist das Kontingent erschoepft, scheitern alle folgenden Abschnitte gleich.
        given(llmClient.complete(anyString(), anyString()))
                .willReturn("[{\"front\": \"A\", \"back\": \"B\"}]")
                .willThrow(new LlmException("Kontingent erschoepft: hoechstens 20 Anfragen pro Tag."));

        runner.run(jobId);

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(store).complete(eq(jobId), note.capture());
        String value = note.getValue();

        assertThat(value).contains("Kontingent erschoepft");
        // Die Ursache steht genau einmal drin, nicht einmal je uebersprungenem Abschnitt.
        assertThat(value.split("Kontingent erschoepft", -1)).hasSize(2);
    }

    @Test
    void listsDifferentCausesSeparately() {
        givenJobWithText(severalChunks());
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new LlmException("Kontingent erschoepft."))
                .willReturn("Kein JSON")
                .willReturn("[{\"front\": \"A\", \"back\": \"B\"}]");

        runner.run(jobId);

        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(store).complete(eq(jobId), note.capture());
        assertThat(note.getValue()).contains("Kontingent erschoepft").contains("JSON-Array");
    }

    @Test
    void skipsChunkWhenTheAnswerIsNotParseable() {
        givenJobWithText(oneChunk());
        given(llmClient.complete(anyString(), anyString()))
                .willReturn("Dazu kann ich leider keine Karten erstellen.");

        runner.run(jobId);

        verify(store).recordSkippedChunk(jobId);
        // Bei nur einem Abschnitt bedeutet uebersprungen zugleich: nichts erreicht.
        verify(store).fail(eq(jobId), anyString());
    }

    @Test
    void failsWhenNoChunkCouldBeProcessed() {
        givenJobWithText(severalChunks());
        given(llmClient.complete(anyString(), anyString()))
                .willThrow(new LlmException("Kontingent aufgebraucht"));

        runner.run(jobId);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(store).fail(eq(jobId), message.capture());
        assertThat(message.getValue()).contains("Kein einziger Abschnitt");
        verify(store, never()).complete(any(), any());
    }

    @Test
    void failsWhenThereIsNotEnoughText() {
        givenJobWithText("Titelblatt");

        runner.run(jobId);

        verify(store).fail(eq(jobId), anyString());
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void removesDuplicatesAcrossChunks() {
        givenJobWithText(severalChunks());
        // Durch den Overlap schlaegt das Modell dieselbe Karte mehrfach vor.
        given(llmClient.complete(anyString(), anyString()))
                .willReturn("[{\"front\": \"Was ist ein Incident?\", \"back\": \"B\"}]");

        runner.run(jobId);

        verify(store, times(1)).saveCards(eq(deckId), any(), any(), any());
        verify(store).recordProcessedChunk(jobId, 1);
        verify(store, times(expectedChunks - 1)).recordProcessedChunk(jobId, 0);
    }

    @Test
    void respectsCardsAlreadyInTheDeck() {
        givenJobWithText(oneChunk());
        given(store.existingFronts(deckId)).willReturn(Set.of("Was ist ein Incident?"));
        given(llmClient.complete(anyString(), anyString()))
                .willReturn("[{\"front\": \"was ist ein incident\", \"back\": \"B\"}]");

        runner.run(jobId);

        verify(store, never()).saveCards(any(), any(), any(), any());
        verify(store).recordProcessedChunk(jobId, 0);
    }

    @Test
    void usesLanguageAndStyleFromTheJob() {
        givenJob(oneChunk(), Language.EN, CardStyle.CLOZE, 8);
        given(llmClient.complete(anyString(), anyString())).willReturn("[]");

        runner.run(jobId);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(systemPrompt.capture(), anyString());
        assertThat(systemPrompt.getValue())
                .contains("englischer Sprache")
                .contains("Lueckentext")
                .contains("hoechstens 8");
    }

    @Test
    void ignoresDocumentThatDisappearedMeanwhile() {
        givenJobWithText(oneChunk());
        given(documentRepository.findById(documentId)).willReturn(Optional.empty());

        runner.run(jobId);

        verify(store).fail(eq(jobId), anyString());
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    private void givenJobWithText(String text) {
        givenJob(text, Language.DE, CardStyle.QA, 5);
    }

    private void givenJob(String text, Language language, CardStyle style, int cardsPerChunk) {
        Document document = new Document("skript.pdf", "application/pdf", 1_000L);
        document.markExtracted(text);
        documentId = document.getId();
        expectedChunks = chunker.chunk(text).size();

        GenerationJob job = mock(GenerationJob.class);
        given(job.getDocumentIds()).willReturn(List.of(documentId));
        given(job.getTargetDeckId()).willReturn(deckId);
        given(job.getLanguage()).willReturn(language);
        given(job.getStyle()).willReturn(style);
        given(job.getCardsPerChunk()).willReturn(cardsPerChunk);

        given(store.load(jobId)).willReturn(job);
        lenient().when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
    }

    private String oneChunk() {
        return SENTENCE.repeat(30);
    }

    /** Lang genug fuer mehrere Abschnitte; die genaue Zahl steht in expectedChunks. */
    private String severalChunks() {
        return SENTENCE.repeat(600);
    }
}
