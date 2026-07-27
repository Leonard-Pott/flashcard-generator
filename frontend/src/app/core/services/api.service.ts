import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DocumentSummary } from '../models/document';
import { Health } from '../models/health';

/**
 * Einziger Zugangspunkt zum Backend. Weitere Endpunkte kommen mit den
 * folgenden Meilensteinen (Decks, Karten, Jobs, Lernmodus, Export).
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  health(): Observable<Health> {
    return this.http.get<Health>(`${this.baseUrl}/health`);
  }

  documents(): Observable<DocumentSummary[]> {
    return this.http.get<DocumentSummary[]>(`${this.baseUrl}/documents`);
  }

  uploadDocument(file: File): Observable<DocumentSummary> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<DocumentSummary>(`${this.baseUrl}/documents`, form);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/documents/${id}`);
  }
}
