import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { concatMap, from } from 'rxjs';

import { ApiService } from '../../core/services/api.service';
import { DocumentSummary } from '../../core/models/document';
import { Deck, Language } from '../../core/models/deck';
import { CardStyle, GenerationBudget } from '../../core/models/generation';
import { apiErrorMessage } from '../../core/api-error';

interface UploadEntry {
  filename: string;
  state: 'pending' | 'running' | 'done' | 'error';
  message?: string;
}

/** Wert der Zieldeck-Auswahl, wenn ein neuer Stapel angelegt werden soll. */
const NEW_DECK = '';

@Component({
  selector: 'app-upload',
  imports: [FormsModule],
  templateUrl: './upload.html',
  styleUrl: './upload.scss'
})
export class UploadPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly documents = signal<DocumentSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly listError = signal<string | null>(null);
  protected readonly dragging = signal(false);
  protected readonly uploads = signal<UploadEntry[]>([]);

  protected readonly decks = signal<Deck[]>([]);
  protected readonly selectedIds = signal<ReadonlySet<string>>(new Set());
  protected readonly starting = signal(false);
  protected readonly generationError = signal<string | null>(null);

  protected targetDeckId = NEW_DECK;
  protected newDeckName = '';
  protected language: Language = 'de';
  protected cardsPerChunk = 5;
  protected style: CardStyle = 'QA';

  protected readonly selectedCount = computed(() => this.selectedIds().size);
  protected readonly canStart = computed(() => this.selectedCount() > 0 && !this.starting());

  protected readonly budget = signal<GenerationBudget | null>(null);

  /**
   * Grobe Schätzung: rund 6.000 Zeichen je Abschnitt. Jeder Abschnitt ist eine Anfrage
   * ans Modell — daran hängt sowohl die Dauer als auch der Verbrauch am Tageskontingent.
   */
  protected readonly estimate = computed(() => {
    const characters = this.documents()
      .filter((document) => this.selectedIds().has(document.id))
      .reduce((sum, document) => sum + document.charCount, 0);
    const chunks = Math.max(1, Math.ceil(characters / 6000));
    const perMinute = this.budget()?.requestsPerMinute ?? 10;
    return { chunks, minutes: Math.max(1, Math.ceil(chunks / perMinute)) };
  });

  /**
   * Warnung, wenn der Lauf mehr Anfragen braucht als der Tarif am Tag hergibt. Ohne die
   * Vorwarnung bleibt er mittendrin im Kontingent stecken, und die restlichen Abschnitte
   * werden übersprungen.
   */
  protected readonly budgetWarning = computed(() => {
    const budget = this.budget();
    if (!budget || this.selectedCount() === 0) {
      return null;
    }
    const { chunks } = this.estimate();
    if (chunks <= budget.dailyRequestBudget) {
      return null;
    }
    return `Der Lauf braucht etwa ${chunks} Anfragen, ${budget.model} erlaubt aber nur `
      + `${budget.dailyRequestBudget} pro Tag. Es entstehen voraussichtlich nur Karten aus den `
      + `ersten ${budget.dailyRequestBudget} Abschnitten, der Rest wird übersprungen. `
      + `Tipp: weniger Dokumente auf einmal auswählen und den Rest morgen ergänzen — `
      + `derselbe Zielstapel funktioniert, Duplikate werden erkannt.`;
  });

  constructor() {
    this.loadDocuments();
    this.loadDecks();
    this.loadBudget();
  }

  private loadBudget(): void {
    this.api.generationBudget().subscribe({
      next: (budget) => this.budget.set(budget),
      // Ohne Budgetangabe entfällt nur die Warnung, der Rest funktioniert weiter.
      error: () => this.budget.set(null)
    });
  }

  private loadDecks(): void {
    this.api.decks().subscribe({
      next: (decks) => this.decks.set(decks),
      error: () => this.decks.set([])
    });
  }

  protected isSelected(document: DocumentSummary): boolean {
    return this.selectedIds().has(document.id);
  }

  protected toggleSelection(document: DocumentSummary): void {
    this.selectedIds.update((current) => {
      const next = new Set(current);
      if (!next.delete(document.id)) {
        next.add(document.id);
      }
      return next;
    });
  }

  protected startGeneration(): void {
    const documentIds = [...this.selectedIds()];
    if (documentIds.length === 0) {
      this.generationError.set('Bitte mindestens ein Dokument auswählen.');
      return;
    }

    const usesNewDeck = this.targetDeckId === NEW_DECK;
    if (usesNewDeck && this.newDeckName.trim().length === 0) {
      this.generationError.set('Bitte einen Namen für den neuen Stapel angeben.');
      return;
    }

    this.starting.set(true);
    this.api
      .createGenerationJob({
        documentIds,
        deckId: usesNewDeck ? null : this.targetDeckId,
        newDeckName: usesNewDeck ? this.newDeckName.trim() : null,
        language: this.language,
        cardsPerChunk: this.cardsPerChunk,
        style: this.style
      })
      .subscribe({
        next: (job) => {
          this.starting.set(false);
          this.router.navigate(['/jobs', job.id]);
        },
        error: (err: unknown) => {
          this.generationError.set(apiErrorMessage(err));
          this.starting.set(false);
        }
      });
  }

  protected loadDocuments(): void {
    this.loading.set(true);
    this.api.documents().subscribe({
      next: (documents) => {
        this.documents.set(documents);
        this.listError.set(null);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.listError.set(this.messageOf(err));
        this.loading.set(false);
      }
    });
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.uploadAll(event.dataTransfer?.files);
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.uploadAll(input.files);
    // Zuruecksetzen, damit dieselbe Datei erneut ausgewaehlt werden kann.
    input.value = '';
  }

  /**
   * Dateien nacheinander hochladen. Tika-Extraktion laeuft synchron im Request,
   * parallele Uploads wuerden nur den Server belasten ohne etwas zu beschleunigen.
   */
  private uploadAll(fileList: FileList | null | undefined): void {
    const files = Array.from(fileList ?? []);
    if (files.length === 0) {
      return;
    }

    this.uploads.set(files.map((file) => ({ filename: file.name, state: 'pending' })));

    from(files)
      .pipe(
        concatMap((file) => {
          this.updateUpload(file.name, { state: 'running' });
          return this.api.uploadDocument(file).pipe();
        })
      )
      .subscribe({
        next: (document) => {
          this.updateUpload(document.filename, {
            state: document.status === 'FAILED' ? 'error' : 'done',
            message: document.errorMessage ?? undefined
          });
          this.documents.update((current) => [document, ...current]);
        },
        error: (err: unknown) => {
          const running = this.uploads().find((entry) => entry.state === 'running');
          if (running) {
            this.updateUpload(running.filename, { state: 'error', message: this.messageOf(err) });
          }
          // Der Rest der Warteschlange wurde durch den Fehler abgebrochen.
          this.uploads.update((entries) =>
            entries.map((entry) =>
              entry.state === 'pending'
                ? { ...entry, state: 'error', message: 'Nicht hochgeladen, vorheriger Fehler.' }
                : entry
            )
          );
        }
      });
  }

  protected deleteDocument(document: DocumentSummary): void {
    this.api.deleteDocument(document.id).subscribe({
      next: () => {
        this.documents.update((current) => current.filter((d) => d.id !== document.id));
        this.selectedIds.update((current) => {
          const next = new Set(current);
          next.delete(document.id);
          return next;
        });
      },
      error: (err: unknown) => this.listError.set(this.messageOf(err))
    });
  }

  protected clearUploads(): void {
    this.uploads.set([]);
  }

  protected formatBytes(bytes: number): string {
    if (bytes < 1024) {
      return `${bytes} B`;
    }
    const units = ['kB', 'MB', 'GB'];
    let value = bytes / 1024;
    let unit = 0;
    while (value >= 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return `${value.toFixed(1)} ${units[unit]}`;
  }

  protected formatNumber(value: number): string {
    return new Intl.NumberFormat('de-DE').format(value);
  }

  protected formatDate(iso: string): string {
    return new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'short' }).format(
      new Date(iso)
    );
  }

  private updateUpload(filename: string, patch: Partial<UploadEntry>): void {
    this.uploads.update((entries) =>
      entries.map((entry) => (entry.filename === filename ? { ...entry, ...patch } : entry))
    );
  }

  private messageOf(err: unknown): string {
    return apiErrorMessage(err);
  }
}
