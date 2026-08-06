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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.leonard.flashcards.deck.DeckDtos.CreateDeckRequest;
import de.leonard.flashcards.deck.DeckDtos.DeckResponse;
import de.leonard.flashcards.deck.DeckDtos.MergeDecksRequest;
import de.leonard.flashcards.deck.DeckDtos.UpdateDeckRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/decks")
public class DeckController {

    private final DeckService service;

    public DeckController(DeckService service) {
        this.service = service;
    }

    @GetMapping
    public List<DeckResponse> list() {
        return service.findAll().stream().map(DeckResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<DeckResponse> create(@Valid @RequestBody CreateDeckRequest request) {
        Deck deck = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/decks/" + deck.getId()))
                .body(DeckResponse.ofNew(deck));
    }

    @GetMapping("/{id}")
    public DeckResponse get(@PathVariable UUID id) {
        return DeckResponse.from(service.findWithCardCount(id));
    }

    @PatchMapping("/{id}")
    public DeckResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDeckRequest request) {
        service.update(id, request);
        return DeckResponse.from(service.findWithCardCount(id));
    }

    /** Uebernimmt die Karten eines anderen Stapels; der gibt es danach nicht mehr. */
    @PostMapping("/{id}/merge")
    public DeckResponse merge(@PathVariable UUID id, @Valid @RequestBody MergeDecksRequest request) {
        service.merge(id, request.sourceDeckId());
        return DeckResponse.from(service.findWithCardCount(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
