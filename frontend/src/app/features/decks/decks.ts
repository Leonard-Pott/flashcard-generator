import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/services/api.service';
import { Deck, Language } from '../../core/models/deck';
import { apiErrorMessage } from '../../core/api-error';

@Component({
  selector: 'app-decks',
  imports: [FormsModule, RouterLink],
  templateUrl: './decks.html',
  styleUrl: './decks.scss'
})
export class DecksPage {
  private readonly api = inject(ApiService);

  protected readonly decks = signal<Deck[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly creating = signal(false);
  protected newName = '';
  protected newDescription = '';
  protected newLanguage: Language = 'de';

  constructor() {
    this.loadDecks();
  }

  protected loadDecks(): void {
    this.loading.set(true);
    this.api.decks().subscribe({
      next: (decks) => {
        this.decks.set(decks);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      }
    });
  }

  protected createDeck(): void {
    if (this.newName.trim().length === 0) {
      this.error.set('Der Name darf nicht leer sein.');
      return;
    }

    this.creating.set(true);
    this.api
      .createDeck({
        name: this.newName.trim(),
        description: this.newDescription.trim() || null,
        language: this.newLanguage
      })
      .subscribe({
        next: (deck) => {
          // Serverseitig wird nach Name sortiert, hier genauso einsortieren.
          this.decks.update((current) =>
            [...current, deck].sort((a, b) =>
              a.name.localeCompare(b.name, 'de', { sensitivity: 'base' })
            )
          );
          this.newName = '';
          this.newDescription = '';
          this.newLanguage = 'de';
          this.error.set(null);
          this.creating.set(false);
        },
        error: (err: unknown) => {
          this.error.set(apiErrorMessage(err));
          this.creating.set(false);
        }
      });
  }

  protected deleteDeck(deck: Deck): void {
    this.api.deleteDeck(deck.id).subscribe({
      next: () => this.decks.update((current) => current.filter((d) => d.id !== deck.id)),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short' }).format(new Date(iso));
  }
}
