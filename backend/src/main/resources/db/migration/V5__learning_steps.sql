-- V5: Lernschritte. Die Faelligkeit wird ein Zeitstempel statt eines Datums,
-- damit "in 10 Minuten" ueberhaupt ausdrueckbar ist.

ALTER TABLE cards ADD COLUMN due_at TIMESTAMPTZ;

-- Ein Datum wird zum Tagesbeginn in der Zeitzone des Servers - genau die Bedeutung,
-- die due_date bisher hatte.
UPDATE cards SET due_at = due_date::timestamp AT TIME ZONE current_setting('TimeZone');

ALTER TABLE cards ALTER COLUMN due_at SET NOT NULL;

DROP INDEX idx_cards_due;
ALTER TABLE cards DROP COLUMN due_date;
CREATE INDEX idx_cards_due ON cards (deck_id, due_at);

-- Index in den Lernschritt-Plan; -1 heisst "laeuft im Tagesrhythmus".
ALTER TABLE cards ADD COLUMN learning_step INTEGER NOT NULL DEFAULT 0;

-- Bestandskarten mit Wiederholungen sind laengst aus den Lernschritten heraus.
UPDATE cards SET learning_step = -1 WHERE repetitions > 0;
