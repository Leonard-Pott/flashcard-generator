export type DocumentStatus = 'UPLOADED' | 'EXTRACTED' | 'FAILED';

/** Metadaten eines hochgeladenen Dokuments (GET /api/documents). */
export interface DocumentSummary {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  uploadedAt: string;
  charCount: number;
  status: DocumentStatus;
  errorMessage: string | null;
}
