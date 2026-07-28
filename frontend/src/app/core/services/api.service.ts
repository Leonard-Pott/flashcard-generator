import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Card, CreateCardRequest, NextCard, UpdateCardRequest } from '../models/card';
import { CreateDeckRequest, Deck, UpdateDeckRequest } from '../models/deck';
import { DocumentSummary } from '../models/document';
import { GenerationJob, GenerationRequest } from '../models/generation';
import { Health } from '../models/health';

/**
 * Einziger Zugangspunkt zum Backend. Weitere Endpunkte kommen mit den
 * folgenden Meilensteinen (Jobs, Lernmodus, Export).
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

  decks(): Observable<Deck[]> {
    return this.http.get<Deck[]>(`${this.baseUrl}/decks`);
  }

  deck(id: string): Observable<Deck> {
    return this.http.get<Deck>(`${this.baseUrl}/decks/${id}`);
  }

  createDeck(request: CreateDeckRequest): Observable<Deck> {
    return this.http.post<Deck>(`${this.baseUrl}/decks`, request);
  }

  updateDeck(id: string, request: UpdateDeckRequest): Observable<Deck> {
    return this.http.patch<Deck>(`${this.baseUrl}/decks/${id}`, request);
  }

  deleteDeck(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/decks/${id}`);
  }

  cards(deckId: string): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.baseUrl}/decks/${deckId}/cards`);
  }

  createCard(deckId: string, request: CreateCardRequest): Observable<Card> {
    return this.http.post<Card>(`${this.baseUrl}/decks/${deckId}/cards`, request);
  }

  updateCard(id: string, request: UpdateCardRequest): Observable<Card> {
    return this.http.patch<Card>(`${this.baseUrl}/cards/${id}`, request);
  }

  deleteCard(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/cards/${id}`);
  }

  /**
   * Adresse des Exports. Wird als normaler Link geöffnet, nicht per XHR geladen —
   * den Download übernimmt dann der Browser anhand von Content-Disposition.
   */
  exportUrl(deckId: string, format: 'csv' | 'anki'): string {
    return `${this.baseUrl}/decks/${deckId}/export?format=${format}`;
  }

  nextCard(deckId: string): Observable<NextCard> {
    return this.http.get<NextCard>(`${this.baseUrl}/decks/${deckId}/study/next`);
  }

  reviewCard(cardId: string, quality: number): Observable<Card> {
    return this.http.post<Card>(`${this.baseUrl}/cards/${cardId}/review`, { quality });
  }

  createGenerationJob(request: GenerationRequest): Observable<GenerationJob> {
    return this.http.post<GenerationJob>(`${this.baseUrl}/generation-jobs`, request);
  }

  generationJob(id: string): Observable<GenerationJob> {
    return this.http.get<GenerationJob>(`${this.baseUrl}/generation-jobs/${id}`);
  }
}
