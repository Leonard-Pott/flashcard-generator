import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ApiService } from './core/services/api.service';
import { Health } from './core/models/health';

type BackendState =
  | { kind: 'loading' }
  | { kind: 'ok'; health: Health }
  | { kind: 'error'; message: string };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly api = inject(ApiService);

  protected readonly title = signal('Karteikarten-Generator');
  protected readonly backend = signal<BackendState>({ kind: 'loading' });

  constructor() {
    this.checkBackend();
  }

  protected checkBackend(): void {
    this.backend.set({ kind: 'loading' });
    this.api.health().subscribe({
      next: (health) => this.backend.set({ kind: 'ok', health }),
      error: (err: unknown) =>
        this.backend.set({
          kind: 'error',
          message: err instanceof Error ? err.message : 'Backend nicht erreichbar'
        })
    });
  }
}
