package de.leonard.flashcards.deck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByDeckIdOrderByCreatedAtAsc(UUID deckId);

    long countByDeckId(UUID deckId);

    /** Aeltester faelliger Termin zuerst; bei gleichem Termin die zuerst angelegte Karte. */
    Optional<Card> findFirstByDeckIdAndDueAtLessThanEqualOrderByDueAtAscCreatedAtAsc(
            UUID deckId, Instant now);

    long countByDeckIdAndDueAtLessThanEqual(UUID deckId, Instant now);

    /** Fuer die Anzeige "naechste Karte ...", wenn gerade nichts faellig ist. */
    Optional<Card> findFirstByDeckIdAndDueAtGreaterThanOrderByDueAtAsc(UUID deckId, Instant now);

    /** Umhaengen beim Zusammenfuehren zweier Stapel - in einer Anweisung statt Karte fuer Karte. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Card c SET c.deck = :target WHERE c.deck.id = :sourceDeckId")
    int moveAllToDeck(UUID sourceDeckId, Deck target);
}
