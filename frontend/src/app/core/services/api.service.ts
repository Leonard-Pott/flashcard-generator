import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Health } from '../models/health';

/**
 * Einziger Zugangspunkt zum Backend. Weitere Endpunkte kommen mit den
 * folgenden Meilensteinen (Dokumente, Decks, Karten, Jobs, Lernmodus, Export).
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  health(): Observable<Health> {
    return this.http.get<Health>(`${this.baseUrl}/health`);
  }
}
