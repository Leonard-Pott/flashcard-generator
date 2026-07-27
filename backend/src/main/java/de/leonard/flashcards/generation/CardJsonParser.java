package de.leonard.flashcards.generation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Liest die Kartenliste aus der Modellantwort. Auch im JSON-Modus wird defensiv
 * vorgegangen: Fences entfernen, den Bereich zwischen erster [ und letzter ] nehmen
 * und erst dann deserialisieren.
 */
@Component
public class CardJsonParser {

    private static final Logger log = LoggerFactory.getLogger(CardJsonParser.class);

    private final ObjectMapper objectMapper;

    public CardJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws CardParseException wenn sich beim besten Willen keine Liste herauslesen laesst;
     *                            der Aufrufer ueberspringt dann den Chunk
     */
    public List<GeneratedCard> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CardParseException("Die Antwort war leer.");
        }

        String json = extractArray(stripFences(raw));
        try {
            List<GeneratedCard> cards = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<GeneratedCard> usable = cards.stream().filter(GeneratedCard::isUsable).toList();

            if (usable.size() < cards.size()) {
                log.debug("{} von {} Karten verworfen, weil Vorder- oder Rueckseite fehlte",
                        cards.size() - usable.size(), cards.size());
            }
            return usable;
        } catch (Exception e) {
            throw new CardParseException("Antwort war kein verwertbares JSON-Array: " + e.getMessage());
        }
    }

    private String stripFences(String raw) {
        String text = raw.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        // ```json ... ``` oder ``` ... ```
        int firstLineBreak = text.indexOf('\n');
        String withoutOpening = firstLineBreak < 0 ? text.substring(3) : text.substring(firstLineBreak + 1);
        int closing = withoutOpening.lastIndexOf("```");
        return (closing < 0 ? withoutOpening : withoutOpening.substring(0, closing)).strip();
    }

    private String extractArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');

        if (start < 0 || end < start) {
            throw new CardParseException("In der Antwort war kein JSON-Array zu finden.");
        }
        return text.substring(start, end + 1);
    }
}
