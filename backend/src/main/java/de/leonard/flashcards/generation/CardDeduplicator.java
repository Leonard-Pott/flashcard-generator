package de.leonard.flashcards.generation;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Erkennt doppelte Karten am normalisierten Text der Vorderseite. Durch den Overlap
 * beim Chunking taucht derselbe Sachverhalt regelmaessig in zwei Abschnitten auf.
 *
 * <p>Ein Exemplar wird pro Lauf angelegt und behaelt die bereits gesehenen Vorderseiten -
 * auch die der Karten, die schon im Zielstapel liegen.
 */
@Component
public class CardDeduplicator {

    public Session newSession(Set<String> existingFronts) {
        Session session = new Session();
        existingFronts.forEach(session::remember);
        return session;
    }

    /** Vergleichsform: ohne Gross-/Kleinschreibung, Satzzeichen und doppelte Leerzeichen. */
    static String normalize(String front) {
        String withoutAccents = Normalizer.normalize(front, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static class Session {

        private final Set<String> seenFronts = new HashSet<>();

        /**
         * @return true, wenn die Karte neu ist; sie gilt danach als gesehen
         */
        public boolean isNew(GeneratedCard card) {
            return seenFronts.add(normalize(card.front()));
        }

        private void remember(String front) {
            seenFronts.add(normalize(front));
        }
    }
}
