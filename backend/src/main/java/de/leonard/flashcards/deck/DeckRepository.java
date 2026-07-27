package de.leonard.flashcards.deck;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    /**
     * Stapel samt Kartenzahl in einer Abfrage - die Liste im Frontend zeigt beides,
     * eine Zaehlung pro Stapel waere ein N+1-Problem.
     */
    @Query("""
            SELECT d AS deck, COUNT(c.id) AS cardCount
            FROM Deck d
            LEFT JOIN Card c ON c.deck = d
            GROUP BY d
            ORDER BY LOWER(d.name)
            """)
    List<DeckWithCardCount> findAllWithCardCount();

    @Query("""
            SELECT d AS deck, COUNT(c.id) AS cardCount
            FROM Deck d
            LEFT JOIN Card c ON c.deck = d
            WHERE d.id = :id
            GROUP BY d
            """)
    Optional<DeckWithCardCount> findWithCardCount(UUID id);

    interface DeckWithCardCount {
        Deck getDeck();

        long getCardCount();
    }
}
