package de.leonard.flashcards.export;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.leonard.flashcards.deck.Card;
import de.leonard.flashcards.deck.CardRepository;
import de.leonard.flashcards.deck.Deck;
import de.leonard.flashcards.deck.DeckService;

@Service
public class ExportService {

    private static final int MAX_FILENAME_LENGTH = 80;

    private final DeckService deckService;
    private final CardRepository cardRepository;
    private final Map<ExportFormat, DeckExportWriter> writers = new EnumMap<>(ExportFormat.class);

    public ExportService(DeckService deckService, CardRepository cardRepository,
                         List<DeckExportWriter> writers) {
        this.deckService = deckService;
        this.cardRepository = cardRepository;
        writers.forEach(writer -> this.writers.put(writer.format(), writer));
    }

    @Transactional(readOnly = true)
    public Export export(UUID deckId, ExportFormat format) {
        Deck deck = deckService.findById(deckId);
        DeckExportWriter writer = writers.get(format);
        if (writer == null) {
            throw new IllegalArgumentException("Fuer das Format " + format + " gibt es keinen Export.");
        }

        List<Card> cards = cardRepository.findAllByDeckIdOrderByCreatedAtAsc(deckId);
        return new Export(
                filenameFor(deck, writer.fileExtension()),
                writer.contentType(),
                writer.write(deck, cards));
    }

    /**
     * Aus dem Stapelnamen einen Dateinamen machen: Umlaute aufloesen, alles was in
     * Dateinamen Aerger macht durch Bindestriche ersetzen.
     */
    private String filenameFor(Deck deck, String extension) {
        String base = Normalizer.normalize(deck.getName(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (base.isEmpty()) {
            base = "stapel";
        }
        if (base.length() > MAX_FILENAME_LENGTH) {
            base = base.substring(0, MAX_FILENAME_LENGTH);
        }
        return base + "." + extension;
    }

    public record Export(String filename, String contentType, byte[] content) {
    }
}
