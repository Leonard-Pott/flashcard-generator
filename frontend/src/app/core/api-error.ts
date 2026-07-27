import { HttpErrorResponse } from '@angular/common/http';

/**
 * Wertet das ProblemDetail des Backends aus. Ohne das käme im Frontend nur
 * "Http failure response for ..." an.
 */
export function apiErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'Backend nicht erreichbar.';
    }
    const detail = (err.error as { detail?: string } | null)?.detail;
    return detail ?? `Fehler ${err.status}: ${err.statusText}`;
  }
  return 'Unbekannter Fehler.';
}
