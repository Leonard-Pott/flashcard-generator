import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/services/api.service';
import { Card } from '../../core/models/card';
import { Deck } from '../../core/models/deck';
import { apiErrorMessage } from '../../core/api-error';

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

  constructor() {
    effect(() => this.load(this.id()));
  }

  private load(deckId: string): void {
    this.loading.set(true);
    this.api.deck(deckId).subscribe({
      next: (deck) => {
        this.deck.set(deck);
        this.loadCards(deckId);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
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
        this.bumpCardCount(-1);
        if (this.editingId() === card.id) {
          this.editingId.set(null);
        }
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  private bumpCardCount(delta: number): void {
    this.deck.update((current) =>
      current ? { ...current, cardCount: current.cardCount + delta } : current
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

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short' }).format(new Date(iso));
  }
}
