package de.leonard.flashcards.deck;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByDeckIdOrderByCreatedAtAsc(UUID deckId);

    long countByDeckId(UUID deckId);

    /** Aelteste faellige Karte zuerst; bei gleichem Datum die zuerst angelegte. */
    Optional<Card> findFirstByDeckIdAndDueDateLessThanEqualOrderByDueDateAscCreatedAtAsc(
            UUID deckId, LocalDate today);

    long countByDeckIdAndDueDateLessThanEqual(UUID deckId, LocalDate today);

    /** Fuer die Anzeige "naechste Karte am ...", wenn gerade nichts faellig ist. */
    Optional<Card> findFirstByDeckIdAndDueDateGreaterThanOrderByDueDateAsc(UUID deckId, LocalDate today);
}
