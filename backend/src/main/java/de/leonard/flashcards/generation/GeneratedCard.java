package de.leonard.flashcards.generation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Eine vom Modell gelieferte Karte, bevor sie zu einer echten Card wird.
 *
 * <p>Zusaetzliche Felder werden ignoriert - Modelle erfinden gerne mal ein
 * "difficulty" oder "id" dazu. Das steht hier explizit und haengt damit nicht an
 * der Konfiguration des jeweils eingereichten ObjectMappers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneratedCard(String front, String back, List<String> tags) {

    public GeneratedCard {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean isUsable() {
        return front != null && !front.isBlank() && back != null && !back.isBlank();
    }
}
