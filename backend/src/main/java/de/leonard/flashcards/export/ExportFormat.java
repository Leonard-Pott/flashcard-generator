package de.leonard.flashcards.export;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExportFormat {
    /** Semikolon-getrennt, UTF-8 mit BOM - so oeffnet Excel die Datei richtig. */
    CSV,
    /** Tabulator-getrennt ohne Kopfzeile, wie es Ankis Import erwartet. */
    ANKI;

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ExportFormat fromCode(String code) {
        if (code == null || code.isBlank()) {
            return CSV;
        }
        return Arrays.stream(values())
                .filter(format -> format.code().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new InvalidExportFormatException(
                        "Unbekanntes Exportformat: " + code + ". Moeglich sind csv und anki."));
    }
}
