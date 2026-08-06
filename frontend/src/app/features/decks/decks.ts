import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/services/api.service';
import { Deck, Language } from '../../core/models/deck';
import { Folder } from '../../core/models/folder';
import { apiErrorMessage } from '../../core/api-error';
import { formatDue } from '../../core/due-time';

/** Ein Ordner samt seiner Stapel; `folder` fehlt bei der Gruppe "Ohne Ordner". */
interface DeckGroup {
  folder: Folder | null;
  decks: Deck[];
}

/** Zugeklappte Ordner überleben den Seitenwechsel, sonst wäre das Zuklappen sinnlos. */
const COLLAPSED_KEY = 'flashcards.collapsedFolders';

@Component({
  selector: 'app-decks',
  imports: [FormsModule, RouterLink],
  templateUrl: './decks.html',
  styleUrl: './decks.scss'
})
export class DecksPage {
  private readonly api = inject(ApiService);

  protected readonly decks = signal<Deck[]>([]);
  protected readonly folders = signal<Folder[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly creating = signal(false);
  protected newName = '';
  protected newDescription = '';
  protected newLanguage: Language = 'de';
  protected newFolderId = '';
  protected newFolderName = '';

  /** Ordner in Namensreihenfolge, "Ohne Ordner" zuletzt und nur, wenn dort etwas liegt. */
  protected readonly groups = computed<DeckGroup[]>(() => {
    const decks = this.decks();
    const grouped: DeckGroup[] = this.folders().map((folder) => ({
      folder,
      decks: decks.filter((deck) => deck.folderId === folder.id)
    }));

    const loose = decks.filter((deck) => deck.folderId === null);
    return loose.length > 0 || grouped.length === 0
      ? [...grouped, { folder: null, decks: loose }]
      : grouped;
  });

  protected readonly formatDue = formatDue;

  private readonly collapsed = new Set<string>(readCollapsed());

  constructor() {
    this.loadDecks();
    this.loadFolders();
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

  private loadFolders(): void {
    this.api.folders().subscribe({
      next: (folders) => this.folders.set(folders),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
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
        language: this.newLanguage,
        folderId: this.newFolderId || null
      })
      .subscribe({
        next: (deck) => {
          this.insertSorted(deck);
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

  protected createFolder(): void {
    const name = this.newFolderName.trim();
    if (name.length === 0) {
      this.error.set('Der Name darf nicht leer sein.');
      return;
    }

    this.api.createFolder(name).subscribe({
      next: (folder) => {
        this.folders.update((current) => [...current, folder].sort(byName));
        this.newFolderName = '';
        this.error.set(null);
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** Der Klick darf nicht bis zum {@code summary} durchschlagen, sonst klappt der Ordner um. */
  protected renameFolder(folder: Folder, event: Event): void {
    event.preventDefault();
    event.stopPropagation();

    const name = prompt('Neuer Name für den Ordner:', folder.name)?.trim();
    if (!name || name === folder.name) {
      return;
    }

    this.api.renameFolder(folder.id, name).subscribe({
      next: (renamed) => {
        this.folders.update((current) =>
          current.map((f) => (f.id === renamed.id ? renamed : f)).sort(byName)
        );
        this.error.set(null);
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** Löscht nur die Gruppierung; die Stapel rutschen in "Ohne Ordner". */
  protected deleteFolder(folder: Folder, event: Event): void {
    event.preventDefault();
    event.stopPropagation();

    this.api.deleteFolder(folder.id).subscribe({
      next: () => {
        this.folders.update((current) => current.filter((f) => f.id !== folder.id));
        this.decks.update((current) =>
          current.map((deck) => (deck.folderId === folder.id ? { ...deck, folderId: null } : deck))
        );
        if (this.newFolderId === folder.id) {
          this.newFolderId = '';
        }
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  protected moveDeck(deck: Deck, folderId: string): void {
    this.api.updateDeck(deck.id, { folderId }).subscribe({
      next: (updated) => {
        this.decks.update((current) => current.map((d) => (d.id === updated.id ? updated : d)));
        this.error.set(null);
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  protected deleteDeck(deck: Deck): void {
    this.api.deleteDeck(deck.id).subscribe({
      next: () => this.decks.update((current) => current.filter((d) => d.id !== deck.id)),
      error: (err: unknown) => this.error.set(apiErrorMessage(err))
    });
  }

  /** Serverseitig wird nach Name sortiert, hier genauso einsortieren. */
  private insertSorted(deck: Deck): void {
    this.decks.update((current) => [...current, deck].sort(byName));
  }

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short' }).format(new Date(iso));
  }

  /** Auch die Gruppe ohne Ordner braucht einen Schlüssel zum Merken. */
  protected groupKey(group: DeckGroup): string {
    return group.folder?.id ?? 'ohne-ordner';
  }

  protected isOpen(key: string): boolean {
    return !this.collapsed.has(key);
  }

  protected setOpen(key: string, open: boolean): void {
    if (open) {
      this.collapsed.delete(key);
    } else {
      this.collapsed.add(key);
    }
    localStorage.setItem(COLLAPSED_KEY, JSON.stringify([...this.collapsed]));
  }
}

function byName(a: { name: string }, b: { name: string }): number {
  return a.name.localeCompare(b.name, 'de', { sensitivity: 'base' });
}

/** Ein kaputter Eintrag darf die Seite nicht lahmlegen - dann eben alles aufgeklappt. */
function readCollapsed(): string[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(COLLAPSED_KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((key) => typeof key === 'string') : [];
  } catch {
    return [];
  }
}
