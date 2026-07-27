package de.leonard.flashcards.generation;

import org.springframework.stereotype.Component;

import de.leonard.flashcards.deck.Language;

/**
 * Baut System- und User-Prompt. Die inhaltlichen Vorgaben stammen aus Abschnitt 7.2
 * des Projektplans - sie machen den Unterschied zwischen brauchbaren und wertlosen Karten.
 */
@Component
public class CardPromptFactory {

    public String systemPrompt(Language language, CardStyle style, int cardsPerChunk) {
        return """
                Du bist ein erfahrener Ersteller von Lernkarten.

                Erzeuge aus dem Text des Nutzers hoechstens %d Karteikarten in %s.

                Kartenstil: %s

                Regeln:
                - Eine Karte prueft genau einen Sachverhalt.
                - Die Vorderseite ist ohne Kenntnis des Dokuments verstaendlich. Formulierungen
                  wie "Was zeigt die Abbildung?" oder "Was steht auf dieser Folie?" sind verboten.
                - Bei Folien-Stichpunkten darfst und sollst du mehrere Punkte zu einer
                  sinnvollen Karte zusammenfassen.
                - Reine Gliederungs-, Titel-, Agenda- und Organisationsfolien enthalten keinen
                  Lernstoff. Gib dafuer ein leeres Array zurueck.
                - Lieber weniger gute Karten als viele belanglose.
                - Vergib pro Karte ein bis drei kurze Tags zum Thema.

                Antworte ausschliesslich mit einem JSON-Array in genau diesem Format:
                [{"front": "...", "back": "...", "tags": ["..."]}]

                Kein Markdown, keine Code-Fences, kein Vorwort, keine Erklaerung.
                """.formatted(cardsPerChunk, languageName(language), styleInstruction(style));
    }

    public String userPrompt(String chunk) {
        return "Erstelle Lernkarten aus dem folgenden Text:\n\n" + chunk;
    }

    private String languageName(Language language) {
        return language == Language.EN ? "englischer Sprache" : "deutscher Sprache";
    }

    private String styleInstruction(CardStyle style) {
        return switch (style) {
            case QA -> """
                    Frage und Antwort. Die Vorderseite stellt eine praezise Frage,
                    die Rueckseite beantwortet sie knapp und vollstaendig.""";
            case CLOZE -> """
                    Lueckentext. Die Vorderseite ist ein Satz aus dem Stoff, in dem der
                    entscheidende Begriff durch [...] ersetzt ist. Die Rueckseite nennt
                    den fehlenden Begriff.""";
            case DEFINITION -> """
                    Begriff und Definition. Die Vorderseite nennt nur den Fachbegriff,
                    die Rueckseite gibt die Definition.""";
        };
    }
}
