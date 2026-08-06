package de.leonard.flashcards.deck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    /**
     * Stapel samt Kartenzahl und Faelligkeiten in einer Abfrage - die Liste im Frontend
     * zeigt alles drei, eine Zaehlung pro Stapel waere ein N+1-Problem.
     *
     * <p>{@code dueToday} zaehlt alles, was bis Tagesende ansteht (Ueberfaelliges
     * eingeschlossen), {@code nextDueAt} ist der naechste Termin nach jetzt.
     */
    @Query("""
            SELECT d AS deck, COUNT(c.id) AS cardCount,
                   COUNT(CASE WHEN c.dueAt < :endOfDay THEN 1 END) AS dueToday,
                   MIN(CASE WHEN c.dueAt > :now THEN c.dueAt END) AS nextDueAt
            FROM Deck d
            LEFT JOIN Card c ON c.deck = d
            GROUP BY d
            ORDER BY LOWER(d.name)
            """)
    List<DeckWithCardCount> findAllWithCardCount(Instant now, Instant endOfDay);

    @Query("""
            SELECT d AS deck, COUNT(c.id) AS cardCount,
                   COUNT(CASE WHEN c.dueAt < :endOfDay THEN 1 END) AS dueToday,
                   MIN(CASE WHEN c.dueAt > :now THEN c.dueAt END) AS nextDueAt
            FROM Deck d
            LEFT JOIN Card c ON c.deck = d
            WHERE d.id = :id
            GROUP BY d
            """)
    Optional<DeckWithCardCount> findWithCardCount(UUID id, Instant now, Instant endOfDay);

    interface DeckWithCardCount {
        Deck getDeck();

        long getCardCount();

        long getDueToday();

        /** Null, wenn im Stapel nichts mehr aussteht. */
        Instant getNextDueAt();
    }
}
