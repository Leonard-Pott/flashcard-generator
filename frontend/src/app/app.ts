import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ApiService } from './core/services/api.service';
import { Health } from './core/models/health';

type BackendState =
  | { kind: 'loading' }
  | { kind: 'ok'; health: Health }
  | { kind: 'error'; message: string };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly api = inject(ApiService);

  protected readonly title = signal('Karteikarten-Generator');
  protected readonly backend = signal<BackendState>({ kind: 'loading' });

  protected readonly backendTooltip = computed(() => {
    const state = this.backend();
    switch (state.kind) {
      case 'ok':
        return `${state.health.application} · Stand ${state.health.timestamp}`;
      case 'error':
        return state.message;
      default:
        return 'Verbindung wird geprüft';
    }
  });

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
