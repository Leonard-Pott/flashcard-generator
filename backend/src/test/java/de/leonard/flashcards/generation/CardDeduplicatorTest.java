package de.leonard.flashcards.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CardDeduplicatorTest {

    private final CardDeduplicator deduplicator = new CardDeduplicator();

    @Test
    void firstOccurrenceIsNew() {
        CardDeduplicator.Session session = deduplicator.newSession(Set.of());

        assertThat(session.isNew(card("Was ist ein Incident?"))).isTrue();
    }

    @Test
    void identicalFrontIsRejected() {
        CardDeduplicator.Session session = deduplicator.newSession(Set.of());
        session.isNew(card("Was ist ein Incident?"));

        assertThat(session.isNew(card("Was ist ein Incident?"))).isFalse();
    }

    @Test
    void ignoresCasePunctuationAndWhitespace() {
        CardDeduplicator.Session session = deduplicator.newSession(Set.of());
        session.isNew(card("Was ist ein Incident?"));

        assertThat(session.isNew(card("was ist ein incident"))).isFalse();
        assertThat(session.isNew(card("Was  ist   ein Incident ?"))).isFalse();
    }

    @Test
    void treatsUmlautSpellingsAsEqual() {
        CardDeduplicator.Session session = deduplicator.newSession(Set.of());
        session.isNew(card("Was bedeutet Verfügbarkeit?"));

        assertThat(session.isNew(card("Was bedeutet VERFÜGBARKEIT"))).isFalse();
    }

    @Test
    void differentQuestionsPassThrough() {
        CardDeduplicator.Session session = deduplicator.newSession(Set.of());
        session.isNew(card("Was ist ein Incident?"));

        assertThat(session.isNew(card("Was ist ein Problem?"))).isTrue();
    }

    @Test
    void knowsCardsAlreadyInTheDeck() {
        // Zweiter Lauf auf denselben Stapel darf keine Dubletten erzeugen.
        CardDeduplicator.Session session = deduplicator.newSession(Set.of("Was ist ein Incident?"));

        assertThat(session.isNew(card("was ist ein incident?"))).isFalse();
        assertThat(session.isNew(card("Was ist ein Change?"))).isTrue();
    }

    private GeneratedCard card(String front) {
        return new GeneratedCard(front, "Antwort", List.of());
    }
}
