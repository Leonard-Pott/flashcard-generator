package de.leonard.flashcards.deck;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "cards")
public class Card {

    /** Startwert des SM-2-Algorithmus (Abschnitt 10 des Projektplans). */
    public static final double INITIAL_EASE_FACTOR = 2.5;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String front;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String back;

    /** Alphabetisch geladen, damit die Anzeige nicht bei jedem Aufruf anders aussieht. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "card_tags", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "tag", nullable = false, length = 64)
    @OrderBy("tag ASC")
    private Set<String> tags = new LinkedHashSet<>();

    /** Herkunft der Karte; bleibt leer bei manuell angelegten Karten. */
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "source_excerpt", columnDefinition = "TEXT")
    private String sourceExcerpt;

    @Column(name = "ease_factor", nullable = false)
    private double easeFactor;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private int repetitions;

    /** Neue Karten sind sofort faellig. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Card() {
        // fuer JPA
    }

    public Card(Deck deck, String front, String back) {
        this.id = UUID.randomUUID();
        this.deck = deck;
        this.front = front;
        this.back = back;
        this.easeFactor = INITIAL_EASE_FACTOR;
        this.intervalDays = 0;
        this.repetitions = 0;
        this.dueDate = LocalDate.now();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void setFront(String front) {
        this.front = front;
        touch();
    }

    public void setBack(String back) {
        this.back = back;
        touch();
    }

    public void setTags(Set<String> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        touch();
    }

    public void setSource(UUID sourceDocumentId, String sourceExcerpt) {
        this.sourceDocumentId = sourceDocumentId;
        this.sourceExcerpt = sourceExcerpt;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Deck getDeck() {
        return deck;
    }

    public String getFront() {
        return front;
    }

    public String getBack() {
        return back;
    }

    public Set<String> getTags() {
        return tags;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public String getSourceExcerpt() {
        return sourceExcerpt;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
