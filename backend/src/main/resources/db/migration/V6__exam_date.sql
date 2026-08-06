-- V6: Pruefungsdatum je Stapel.
--
-- SM-2 optimiert auf dauerhaftes Behalten mit moeglichst wenigen Wiederholungen. Vor einem
-- festen Termin ist das die falsche Zielgroesse: eine Karte mit 15 Tagen Intervall sieht
-- man vor der Pruefung kein einziges Mal wieder. Ist ein Datum gesetzt, deckelt der
-- Scheduler die Intervalle auf die halbe Restzeit.

ALTER TABLE decks ADD COLUMN exam_date DATE;
