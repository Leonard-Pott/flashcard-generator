-- V1 Baseline.
-- Noch keine fachlichen Tabellen: documents folgt mit M2, decks/cards mit M3,
-- generation_jobs mit M5. Diese Migration legt nur die Grundlagen.

-- gen_random_uuid() ist ab Postgres 13 im Core enthalten; pgcrypto als Absicherung
-- fuer den Fall, dass spaeter eine aeltere Version verwendet wird.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
