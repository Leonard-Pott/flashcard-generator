package de.leonard.flashcards.deck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "decks")
public class Deck {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    /** Optional. Ist es gesetzt, deckelt der Scheduler die Intervalle auf die Restzeit. */
    @Column(name = "exam_date")
    private LocalDate examDate;

    /**
     * Optionale Gruppierung. Bewusst nur die Id statt einer Beziehung - der Stapel
     * braucht vom Ordner nichts ausser der Zugehoerigkeit.
     */
    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Deck() {
        // fuer JPA
    }

    public Deck(String name, String description, Language language) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.language = language;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void rename(String name) {
        this.name = name;
        touch();
    }

    public void describe(String description) {
        this.description = description;
        touch();
    }

    public void useLanguage(Language language) {
        this.language = language;
        touch();
    }

    /** {@code null} schaltet den Deckel wieder ab. */
    public void examOn(LocalDate examDate) {
        this.examDate = examDate;
        touch();
    }

    /** {@code null} nimmt den Stapel aus seinem Ordner heraus. */
    public void moveTo(UUID folderId) {
        this.folderId = folderId;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Language getLanguage() {
        return language;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
