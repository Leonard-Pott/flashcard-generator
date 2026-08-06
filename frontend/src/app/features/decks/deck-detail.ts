import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/services/api.service';
import { Card } from '../../core/models/card';
import { Deck, UpdateDeckRequest } from '../../core/models/deck';
import { Folder } from '../../core/models/folder';
import { apiErrorMessage } from '../../core/api-error';
import { formatDue } from '../../core/due-time';

@Component({
  selector: 'app-deck-detail',
  imports: [FormsModule, RouterLink],
  templateUrl: './deck-detail.html',
  styleUrl: './deck-detail.scss'
})
export class DeckDetailPage {
  /** Kommt aus dem Routenparameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);

  protected readonly deck = signal<Deck | null>(null);
  protected readonly cards = signal<Card[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly editingId = signal<string | null>(null);
  protected editFront = '';
  protected editBack = '';
  protected editTags = '';

  protected newFront = '';
  protected newBack = '';
  protected newTags = '';

  /** Rohwert des Datumsfelds; leer heißt "kein Prüfungstermin". */
  protected examDate = '';

  protected deckName = '';
  protected folderId = '';
  protected readonly folders = signal<Folder[]>([]);

  /** Kandidaten zum Zusammenführen: alle Stapel ausser diesem. */
  protected readonly otherDecks = signal<Deck[]>([]);
  protected mergeSourceId = '';
  protected readonly merging = signal(false);

  constructor() {
    effect(() => this.load(this.id()));
  }

  private load(deckId: string): void {
    this.loading.set(true);
    this.api.deck(deckId).subscribe({
      next: (deck) => {
        this.applyDeck(deck);
        this.loadCards(deckId);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
    this.api.folders().subscribe({
      next: (folders) => this.folders.set(folders),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
    this.api.decks().subscribe({
      next: (decks) => this.otherDecks.set(decks.filter((deck) => deck.id !== deckId)),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** Hält die Formularfelder am Stand des Servers. */
  private applyDeck(deck: Deck): void {
    this.deck.set(deck);
    this.deckName = deck.name;
    this.examDate = deck.examDate ?? '';
    this.folderId = deck.folderId ?? '';
  }

  private loadCards(deckId: string): void {
    this.api.cards(deckId).subscribe({
      next: (cards) => {
        this.cards.set(cards);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  protected addCard(): void {
    if (this.newFront.trim().length === 0 || this.newBack.trim().length === 0) {
      this.error.set('Vorder- und Rückseite dürfen nicht leer sein.');
      return;
    }

    this.api
      .createCard(this.id(), {
        front: this.newFront.trim(),
        back: this.newBack.trim(),
        tags: this.parseTags(this.newTags)
      })
      .subscribe({
        next: (card) => {
          this.cards.update((current) => [...current, card]);
          this.bumpCardCount(1);
          this.newFront = '';
          this.newBack = '';
          this.newTags = '';
          this.error.set(null);
        },
        error: (err: unknown) => this.error.set(apiErrorMessage(err))
      });
  }

  /** Ein leeres Feld entfernt den Termin, statt ihn stehen zu lassen. */
  protected saveExamDate(): void {
    this.patchDeck({ examDate: this.examDate });
  }

  protected saveFolder(): void {
    this.patchDeck({ folderId: this.folderId });
  }

  protected saveName(): void {
    const name = this.deckName.trim();
    if (name.length === 0) {
      this.error.set('Der Name darf nicht leer sein.');
      return;
    }
    if (name === this.deck()?.name) {
      return;
    }
    this.patchDeck({ name });
  }

  /** Übernimmt die Karten eines anderen Stapels; dieser hier bleibt bestehen. */
  protected mergeSelected(): void {
    const sourceId = this.mergeSourceId;
    if (!sourceId) {
      return;
    }

    this.merging.set(true);
    this.api.mergeDecks(this.id(), sourceId).subscribe({
      next: (deck) => {
        this.applyDeck(deck);
        this.otherDecks.update((current) => current.filter((other) => other.id !== sourceId));
        this.mergeSourceId = '';
        this.merging.set(false);
        this.error.set(null);
        this.loadCards(this.id());
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.merging.set(false);
      }
    });
  }

  private patchDeck(request: UpdateDeckRequest): void {
    this.api.updateDeck(this.id(), request).subscribe({
      next: (deck) => {
        this.applyDeck(deck);
        this.error.set(null);
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** Tage bis zur Prüfung; negativ, wenn sie vorbei ist. */
  protected daysUntilExam(examDate: string): number {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    return Math.round((new Date(examDate).getTime() - start.getTime()) / 86_400_000);
  }

  /** Spiegelt den Deckel aus Sm2.capBeforeExam: halbe Restzeit, mindestens ein Tag. */
  protected examCap(examDate: string): number {
    return Math.max(1, Math.floor(this.daysUntilExam(examDate) / 2));
  }

  protected startEdit(card: Card): void {
    this.editingId.set(card.id);
    this.editFront = card.front;
    this.editBack = card.back;
    this.editTags = card.tags.join(', ');
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected saveEdit(card: Card): void {
    this.api
      .updateCard(card.id, {
        front: this.editFront,
        back: this.editBack,
        tags: this.parseTags(this.editTags)
      })
      .subscribe({
        next: (updated) => {
          this.cards.update((current) => current.map((c) => (c.id === updated.id ? updated : c)));
          this.editingId.set(null);
          this.error.set(null);
        },
        error: (err: unknown) => this.error.set(apiErrorMessage(err))
      });
  }

  protected deleteCard(card: Card): void {
    this.api.deleteCard(card.id).subscribe({
      next: () => {
        this.cards.update((current) => current.filter((c) => c.id !== card.id));
        // Nur mitzählen, was heute überhaupt noch angestanden hätte.
        const dueToday = new Date(card.dueAt).getTime() < new Date().setHours(24, 0, 0, 0);
        this.bumpCardCount(-1, dueToday ? -1 : 0);
        if (this.editingId() === card.id) {
          this.editingId.set(null);
        }
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** {@code dueDelta} weicht ab, wenn die Karte gar nicht für heute anstand. */
  private bumpCardCount(delta: number, dueDelta = delta): void {
    this.deck.update((current) =>
      current
        ? {
            ...current,
            cardCount: current.cardCount + delta,
            dueToday: current.dueToday + dueDelta
          }
        : current
    );
  }

  private parseTags(raw: string): string[] {
    return raw
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag) => tag.length > 0);
  }

  protected exportUrl(format: 'csv' | 'anki'): string {
    return this.api.exportUrl(this.id(), format);
  }

  protected readonly formatDue = formatDue;

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short' }).format(new Date(iso));
  }
}
