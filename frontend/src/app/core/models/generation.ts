import { Language } from './deck';

export type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
export type CardStyle = 'QA' | 'CLOZE' | 'DEFINITION';

/** Entweder deckId oder newDeckName setzen, nicht beides. */
export interface GenerationRequest {
  documentIds: string[];
  deckId?: string | null;
  newDeckName?: string | null;
  language?: Language;
  cardsPerChunk?: number;
  style?: CardStyle;
}

export interface GenerationJob {
  id: string;
  status: JobStatus;
  targetDeckId: string | null;
  documentIds: string[];
  language: Language;
  style: CardStyle;
  cardsPerChunk: number;
  totalChunks: number;
  processedChunks: number;
  skippedChunks: number;
  generatedCardCount: number;
  llmModel: string | null;
  errorMessage: string | null;
  createdAt: string;
  finishedAt: string | null;
}

/** Rahmenbedingungen des Tarifs (GET /api/generation-jobs/budget). */
export interface GenerationBudget {
  model: string;
  dailyRequestBudget: number;
  requestsPerMinute: number;
}

export function isFinished(job: GenerationJob): boolean {
  return job.status === 'COMPLETED' || job.status === 'FAILED';
}
