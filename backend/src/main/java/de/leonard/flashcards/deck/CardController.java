package de.leonard.flashcards.deck;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.leonard.flashcards.deck.CardDtos.CardResponse;
import de.leonard.flashcards.deck.CardDtos.CreateCardRequest;
import de.leonard.flashcards.deck.CardDtos.UpdateCardRequest;

import jakarta.validation.Valid;

/**
 * Karten haengen beim Anlegen und Auflisten am Stapel, werden aber ueber ihre
 * eigene ID geaendert und geloescht - so steht es in der API-Uebersicht des Plans.
 */
@RestController
public class CardController {

    private final CardService service;

    public CardController(CardService service) {
        this.service = service;
    }

    @GetMapping("/api/decks/{deckId}/cards")
    public List<CardResponse> list(@PathVariable UUID deckId) {
        return service.findByDeck(deckId).stream().map(CardResponse::from).toList();
    }

    @PostMapping("/api/decks/{deckId}/cards")
    public ResponseEntity<CardResponse> create(
            @PathVariable UUID deckId, @Valid @RequestBody CreateCardRequest request) {
        Card card = service.create(deckId, request);
        return ResponseEntity
                .created(URI.create("/api/cards/" + card.getId()))
                .body(CardResponse.from(card));
    }

    @PatchMapping("/api/cards/{id}")
    public CardResponse update(@PathVariable UUID id, @RequestBody UpdateCardRequest request) {
        return CardResponse.from(service.update(id, request));
    }

    @DeleteMapping("/api/cards/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
