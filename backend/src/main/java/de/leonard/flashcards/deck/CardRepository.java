package de.leonard.flashcards.deck;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByDeckIdOrderByCreatedAtAsc(UUID deckId);

    long countByDeckId(UUID deckId);
}
