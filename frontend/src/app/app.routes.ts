import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'upload' },
  {
    path: 'upload',
    title: 'Dokumente',
    loadComponent: () => import('./features/upload/upload').then((m) => m.UploadPage)
  },
  {
    path: 'decks',
    title: 'Stapel',
    loadComponent: () => import('./features/decks/decks').then((m) => m.DecksPage)
  },
  {
    path: 'decks/:id',
    title: 'Stapel',
    loadComponent: () => import('./features/decks/deck-detail').then((m) => m.DeckDetailPage)
  },
  { path: '**', redirectTo: 'upload' }
];
