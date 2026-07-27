import { Component, HostListener, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/services/api.service';
import { Card } from '../../core/models/card';
import { Deck } from '../../core/models/deck';
import { apiErrorMessage } from '../../core/api-error';

/**
 * Die vier Knöpfe des Lernmodus. SM-2 rechnet mit 0 bis 5; die Zwischenstufen 1 und 2
 * sind für den Lernenden nicht unterscheidbar und deshalb nicht belegt.
 */
interface Rating {
  quality: number;
  label: string;
  key: string;
}

const RATINGS: Rating[] = [
  { quality: 0, label: 'Nochmal', key: '1' },
  { quality: 3, label: 'Schwer', key: '2' },
  { quality: 4, label: 'Gut', key: '3' },
  { quality: 5, label: 'Einfach', key: '4' }
];

@Component({
  selector: 'app-study',
  imports: [RouterLink],
  templateUrl: './study.html',
  styleUrl: './study.scss'
})
export class StudyPage {
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);

  protected readonly ratings = RATINGS;

  protected readonly deck = signal<Deck | null>(null);
  protected readonly card = signal<Card | null>(null);
  protected readonly dueCount = signal(0);
  protected readonly nextDueDate = signal<string | null>(null);
  protected readonly revealed = signal(false);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reviewedCount = signal(0);

  protected readonly done = computed(() => !this.loading() && this.card() === null);

  constructor() {
    effect(() => {
      const deckId = this.id();
      this.loadDeck(deckId);
      this.loadNext(deckId);
    });
  }

  private loadDeck(deckId: string): void {
    this.api.deck(deckId).subscribe({
      next: (deck) => this.deck.set(deck),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  private loadNext(deckId: string): void {
    this.loading.set(true);
    this.revealed.set(false);
    this.api.nextCard(deckId).subscribe({
      next: (next) => {
        this.card.set(next.card);
        this.dueCount.set(next.dueCount);
        this.nextDueDate.set(next.nextDueDate);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  protected reveal(): void {
    if (this.card()) {
      this.revealed.set(true);
    }
  }

  protected rate(quality: number): void {
    const card = this.card();
    if (!card || !this.revealed() || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.api.reviewCard(card.id, quality).subscribe({
      next: () => {
        this.reviewedCount.update((count) => count + 1);
        this.saving.set(false);
        this.loadNext(this.id());
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.saving.set(false);
      }
    });
  }

  /** Leertaste deckt auf, danach bewerten die Tasten 1 bis 4. */
  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
      return;
    }

    if (!this.revealed()) {
      if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault();
        this.reveal();
      }
      return;
    }

    const rating = RATINGS.find((candidate) => candidate.key === event.key);
    if (rating) {
      event.preventDefault();
      this.rate(rating.quality);
    }
  }

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'long' }).format(new Date(iso));
  }
}
