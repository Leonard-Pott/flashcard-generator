export type Language = 'de' | 'en';

export interface Deck {
  id: string;
  name: string;
  description: string | null;
  language: Language;
  cardCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDeckRequest {
  name: string;
  description?: string | null;
  language?: Language;
}

/** Nur gesetzte Felder werden geändert; ein leerer String leert das Feld. */
export interface UpdateDeckRequest {
  name?: string;
  description?: string;
  language?: Language;
}
