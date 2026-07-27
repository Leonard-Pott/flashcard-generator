package de.leonard.flashcards.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckService;
import de.leonard.flashcards.document.Document;
import de.leonard.flashcards.document.DocumentRepository;
import de.leonard.flashcards.document.DocumentStatus;
import de.leonard.flashcards.generation.GenerationDtos.GenerationRequest;

@Service
public class GenerationService {

    private final GenerationJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DeckService deckService;

    public GenerationService(GenerationJobRepository jobRepository, DocumentRepository documentRepository,
                             DeckService deckService) {
        this.jobRepository = jobRepository;
        this.deckService = deckService;
        this.documentRepository = documentRepository;
    }

    /**
     * Legt den Job an und prueft alles, was sich vorher pruefen laesst. Die eigentliche
     * Arbeit uebernimmt danach der GenerationRunner.
     */
    @Transactional
    public GenerationJob createJob(GenerationRequest request) {
        List<UUID> documentIds = validateDocuments(request.documentIds());
        UUID deckId = resolveDeck(request);

        GenerationJob job = new GenerationJob(
                documentIds,
                deckId,
                request.languageOrDefault(),
                request.styleOrDefault(),
                request.cardsPerChunkOrDefault());
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public GenerationJob findById(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new GenerationJobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<GenerationJob> findAll() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Dokumente ohne Textebene haben nichts beizutragen. Sie hier abzulehnen ist
     * ehrlicher, als sie stillschweigend zu ueberspringen und am Ende null Karten
     * zu liefern (siehe Abschnitt 7.1 des Projektplans).
     */
    private List<UUID> validateDocuments(List<UUID> requestedIds) {
        List<UUID> distinctIds = requestedIds.stream().distinct().toList();
        List<Document> documents = documentRepository.findAllById(distinctIds);

        if (documents.size() != distinctIds.size()) {
            List<UUID> found = documents.stream().map(Document::getId).toList();
            List<UUID> missing = new ArrayList<>(distinctIds);
            missing.removeAll(found);
            throw new InvalidGenerationRequestException("Unbekannte Dokumente: " + missing);
        }

        List<String> unusable = documents.stream()
                .filter(document -> document.getStatus() != DocumentStatus.EXTRACTED)
                .map(Document::getFilename)
                .toList();
        if (!unusable.isEmpty()) {
            throw new InvalidGenerationRequestException(
                    "Aus diesen Dokumenten wurde kein Text extrahiert: " + String.join(", ", unusable));
        }
        return distinctIds;
    }

    private UUID resolveDeck(GenerationRequest request) {
        boolean hasDeckId = request.deckId() != null;
        boolean hasNewName = StringUtils.hasText(request.newDeckName());

        if (hasDeckId == hasNewName) {
            throw new InvalidGenerationRequestException(
                    "Entweder einen bestehenden Stapel waehlen oder einen Namen fuer einen neuen angeben.");
        }
        if (hasDeckId) {
            // Wirft, wenn es den Stapel nicht gibt.
            return deckService.findById(request.deckId()).getId();
        }

        Deck deck = deckService.create(new CreateDeckRequest(
                request.newDeckName(), null, request.languageOrDefault()));
        return deck.getId();
    }
}
