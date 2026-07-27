import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'upload' },
  {
    path: 'upload',
    title: 'Dokumente',
    loadComponent: () => import('./features/upload/upload').then((m) => m.UploadPage)
  },
  { path: '**', redirectTo: 'upload' }
];
