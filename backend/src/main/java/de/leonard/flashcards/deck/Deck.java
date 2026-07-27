package de.leonard.flashcards.deck;

import java.time.Instant;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
