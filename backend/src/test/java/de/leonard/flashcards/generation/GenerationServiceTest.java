package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckService;
import de.leonard.flashcards.deck.Language;
import de.leonard.flashcards.document.Document;
import de.leonard.flashcards.document.DocumentRepository;
import de.leonard.flashcards.generation.GenerationDtos.GenerationRequest;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private GenerationJobRepository jobRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DeckService deckService;

    private GenerationService service;

    @BeforeEach
    void setUp() {
        service = new GenerationService(jobRepository, documentRepository, deckService);
        lenient().when(jobRepository.save(any(GenerationJob.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsJobForExistingDeck() {
        Document document = extractedDocument();
        Deck deck = new Deck("ITIL", null, Language.DE);
        given(documentRepository.findAllById(List.of(document.getId()))).willReturn(List.of(document));
        given(deckService.findById(deck.getId())).willReturn(deck);

        GenerationJob job = service.createJob(new GenerationRequest(
                List.of(document.getId()), deck.getId(), null, Language.DE, 5, CardStyle.QA));

        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getTargetDeckId()).isEqualTo(deck.getId());
        assertThat(job.getDocumentIds()).containsExactly(document.getId());
        assertThat(job.getTotalChunks()).isZero();
    }

    @Test
    void createsNewDeckWhenOnlyANameIsGiven() {
        Document document = extractedDocument();
        Deck created = new Deck("Neuer Stapel", null, Language.EN);
        given(documentRepository.findAllById(List.of(document.getId()))).willReturn(List.of(document));
        given(deckService.create(any(CreateDeckRequest.class))).willReturn(created);

        GenerationJob job = service.createJob(new GenerationRequest(
                List.of(document.getId()), null, "Neuer Stapel", Language.EN, 5, CardStyle.QA));

        assertThat(job.getTargetDeckId()).isEqualTo(created.getId());
    }

    @Test
    void appliesDefaultsForLanguageStyleAndCardsPerChunk() {
        Document document = extractedDocument();
        Deck deck = new Deck("ITIL", null, Language.DE);
        given(documentRepository.findAllById(List.of(document.getId()))).willReturn(List.of(document));
        given(deckService.findById(deck.getId())).willReturn(deck);

        GenerationJob job = service.createJob(new GenerationRequest(
                List.of(document.getId()), deck.getId(), null, null, null, null));

        assertThat(job.getLanguage()).isEqualTo(Language.DE);
        assertThat(job.getStyle()).isEqualTo(CardStyle.QA);
        // Passend zu den 30.000 Zeichen je Abschnitt.
        assertThat(job.getCardsPerChunk()).isEqualTo(15);
    }

    @Test
    void rejectsBothDeckIdAndNewDeckName() {
        Document document = extractedDocument();
        given(documentRepository.findAllById(any())).willReturn(List.of(document));

        assertThatThrownBy(() -> service.createJob(new GenerationRequest(
                List.of(document.getId()), UUID.randomUUID(), "Auch ein Name", Language.DE, 5, CardStyle.QA)))
                .isInstanceOf(InvalidGenerationRequestException.class)
                .hasMessageContaining("Entweder");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void rejectsNeitherDeckIdNorNewDeckName() {
        Document document = extractedDocument();
        given(documentRepository.findAllById(any())).willReturn(List.of(document));

        assertThatThrownBy(() -> service.createJob(new GenerationRequest(
                List.of(document.getId()), null, "   ", Language.DE, 5, CardStyle.QA)))
                .isInstanceOf(InvalidGenerationRequestException.class);
    }

    @Test
    void rejectsDocumentsWithoutExtractedText() {
        Document failed = new Document("scan.pdf", "application/pdf", 100L);
        failed.markFailed("Kein Text gefunden.");
        given(documentRepository.findAllById(any())).willReturn(List.of(failed));

        assertThatThrownBy(() -> service.createJob(new GenerationRequest(
                List.of(failed.getId()), UUID.randomUUID(), null, Language.DE, 5, CardStyle.QA)))
                .isInstanceOf(InvalidGenerationRequestException.class)
                .hasMessageContaining("scan.pdf");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownDocuments() {
        UUID unknown = UUID.randomUUID();
        given(documentRepository.findAllById(List.of(unknown))).willReturn(List.of());

        assertThatThrownBy(() -> service.createJob(new GenerationRequest(
                List.of(unknown), UUID.randomUUID(), null, Language.DE, 5, CardStyle.QA)))
                .isInstanceOf(InvalidGenerationRequestException.class)
                .hasMessageContaining("Unbekannte Dokumente");
    }

    @Test
    void deduplicatesDocumentIds() {
        Document document = extractedDocument();
        Deck deck = new Deck("ITIL", null, Language.DE);
        given(documentRepository.findAllById(List.of(document.getId()))).willReturn(List.of(document));
        given(deckService.findById(deck.getId())).willReturn(deck);

        GenerationJob job = service.createJob(new GenerationRequest(
                List.of(document.getId(), document.getId()), deck.getId(), null, Language.DE, 5, CardStyle.QA));

        assertThat(job.getDocumentIds()).containsExactly(document.getId());
    }

    private Document extractedDocument() {
        Document document = new Document("skript.pdf", "application/pdf", 1_000L);
        document.markExtracted("Ein Text mit Inhalt.");
        return document;
    }
}
