import { Component, inject, signal } from '@angular/core';
import { concatMap, from } from 'rxjs';

import { ApiService } from '../../core/services/api.service';
import { DocumentSummary } from '../../core/models/document';
import { apiErrorMessage } from '../../core/api-error';

interface UploadEntry {
  filename: string;
  state: 'pending' | 'running' | 'done' | 'error';
  message?: string;
}

@Component({
  selector: 'app-upload',
  templateUrl: './upload.html',
  styleUrl: './upload.scss'
})
export class UploadPage {
  private readonly api = inject(ApiService);

  protected readonly documents = signal<DocumentSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly listError = signal<string | null>(null);
  protected readonly dragging = signal(false);
  protected readonly uploads = signal<UploadEntry[]>([]);

  constructor() {
    this.loadDocuments();
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
      next: () => this.documents.update((current) => current.filter((d) => d.id !== document.id)),
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
