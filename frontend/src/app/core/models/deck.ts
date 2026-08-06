export type Language = 'de' | 'en';

export interface Deck {
  id: string;
  name: string;
  description: string | null;
  language: Language;
  /** Ist es gesetzt, deckelt der Scheduler die Intervalle auf die Restzeit. */
  examDate: string | null;
  /** Ordner, in dem der Stapel liegt; `null` heißt "ohne Ordner". */
  folderId: string | null;
  cardCount: number;
  /** Heute noch offene Karten, Überfälliges eingeschlossen. */
  dueToday: number;
  /** Nächster Termin nach jetzt; `null`, wenn nichts mehr aussteht. */
  nextDueAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDeckRequest {
  name: string;
  description?: string | null;
  language?: Language;
  folderId?: string | null;
}

/** Nur mitgeschickte Felder werden geändert; ein leerer String leert das Feld. */
export interface UpdateDeckRequest {
  name?: string;
  description?: string;
  language?: Language;
  /** Leerer String entfernt den Prüfungstermin. */
  examDate?: string;
  /** Leerer String nimmt den Stapel aus seinem Ordner heraus. */
  folderId?: string;
}
