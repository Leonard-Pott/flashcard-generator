import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subscription, interval, startWith, switchMap } from 'rxjs';

import { ApiService } from '../../core/services/api.service';
import { GenerationJob, isFinished } from '../../core/models/generation';
import { apiErrorMessage } from '../../core/api-error';

/** Der Plan sieht Polling im Zwei-Sekunden-Takt vor; SSE waere eine spätere Verbesserung. */
const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-job-progress',
  imports: [RouterLink],
  templateUrl: './job-progress.html',
  styleUrl: './job-progress.scss'
})
export class JobProgressPage {
  readonly id = input.required<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly job = signal<GenerationJob | null>(null);
  protected readonly error = signal<string | null>(null);

  private polling?: Subscription;

  protected readonly percent = computed(() => {
    const job = this.job();
    if (!job || job.totalChunks === 0) {
      return 0;
    }
    return Math.round((job.processedChunks / job.totalChunks) * 100);
  });

  /** Restdauer aus offenen Abschnitten und der Drosselung auf etwa 10 Anfragen pro Minute. */
  protected readonly remainingMinutes = computed(() => {
    const job = this.job();
    if (!job || job.totalChunks === 0 || isFinished(job)) {
      return 0;
    }
    return Math.max(1, Math.ceil((job.totalChunks - job.processedChunks) / 10));
  });

  constructor() {
    effect(() => this.startPolling(this.id()));
    this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
  }

  private startPolling(jobId: string): void {
    this.polling?.unsubscribe();
    this.polling = interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.generationJob(jobId)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (job) => {
          this.job.set(job);
          this.error.set(null);
          if (isFinished(job)) {
            // Fertig: kein weiteres Nachfragen.
            this.polling?.unsubscribe();
          }
        },
        error: (err: unknown) => {
          this.error.set(apiErrorMessage(err));
          this.polling?.unsubscribe();
        }
      });
  }

  protected openDeck(): void {
    const deckId = this.job()?.targetDeckId;
    if (deckId) {
      this.router.navigate(['/decks', deckId]);
    }
  }

  protected statusLabel(status: GenerationJob['status']): string {
    switch (status) {
      case 'PENDING':
        return 'Wartet auf Start';
      case 'RUNNING':
        return 'Läuft';
      case 'COMPLETED':
        return 'Fertig';
      case 'FAILED':
        return 'Fehlgeschlagen';
    }
  }

  protected isFinished(job: GenerationJob): boolean {
    return isFinished(job);
  }
}
