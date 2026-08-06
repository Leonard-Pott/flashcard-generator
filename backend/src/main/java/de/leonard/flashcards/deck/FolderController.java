package de.leonard.flashcards.deck;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ordner sind reine Gruppierung ohne eigene Fachlogik - Anlegen, Umbenennen, Loeschen.
 * Die Zuordnung eines Stapels laeuft ueber PATCH /api/decks/{id} mit {@code folderId}.
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository repository;

    public FolderController(FolderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FolderResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(FolderResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<FolderResponse> create(@Valid @RequestBody FolderRequest request) {
        Folder folder = repository.save(new Folder(request.name().trim()));
        return ResponseEntity
                .created(URI.create("/api/folders/" + folder.getId()))
                .body(FolderResponse.from(folder));
    }

    @PatchMapping("/{id}")
    @Transactional
    public FolderResponse rename(@PathVariable UUID id, @Valid @RequestBody FolderRequest request) {
        Folder folder = repository.findById(id).orElseThrow(() -> new FolderNotFoundException(id));
        folder.rename(request.name().trim());
        return FolderResponse.from(folder);
    }

    /** Die enthaltenen Stapel bleiben bestehen und liegen danach ausserhalb (ON DELETE SET NULL). */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repository.existsById(id)) {
            throw new FolderNotFoundException(id);
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record FolderRequest(
            @NotBlank(message = "Der Name darf nicht leer sein.")
            @Size(max = 200, message = "Der Name darf hoechstens 200 Zeichen lang sein.")
            String name) {
    }

    public record FolderResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

        static FolderResponse from(Folder folder) {
            return new FolderResponse(
                    folder.getId(), folder.getName(), folder.getCreatedAt(), folder.getUpdatedAt());
        }
    }
}
