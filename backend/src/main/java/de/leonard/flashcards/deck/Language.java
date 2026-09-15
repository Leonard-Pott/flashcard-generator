package de.leonard.flashcards.deck;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Sprache eines Stapels. Nach aussen als "de" / "en", damit das Frontend und der
 * GenerationRequest dieselben Werte verwenden.
 */
public enum Language {
    DE,
    EN;

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Language fromCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim().toLowerCase()) {
            case "de" -> DE;
            case "en" -> EN;
            default -> throw new IllegalArgumentException("Unbekannte Sprache: " + code);
        };
    }
}
