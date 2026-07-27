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
