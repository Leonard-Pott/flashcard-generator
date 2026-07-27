/** Antwort von GET /api/health. */
export interface Health {
  status: string;
  application: string;
  database: string;
  timestamp: string;
}
