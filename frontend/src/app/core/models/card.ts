export interface Card {
  id: string;
  deckId: string;
  front: string;
  back: string;
  tags: string[];
  sourceDocumentId: string | null;
  sourceExcerpt: string | null;
  easeFactor: number;
  intervalDays: number;
  repetitions: number;
  dueDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCardRequest {
  front: string;
  back: string;
  tags?: string[];
  sourceDocumentId?: string | null;
  sourceExcerpt?: string | null;
}

/** Nur gesetzte Felder werden geändert. */
export interface UpdateCardRequest {
  front?: string;
  back?: string;
  tags?: string[];
}

/** Antwort von GET /api/decks/{id}/study/next. */
export interface NextCard {
  /** null, wenn für heute nichts mehr fällig ist. */
  card: Card | null;
  dueCount: number;
  /** Gesetzt, wenn nichts fällig ist: wann die nächste Karte drankommt. */
  nextDueDate: string | null;
}

