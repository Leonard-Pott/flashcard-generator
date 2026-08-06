-- V7: Ordner fuer Stapel, etwa je Uni-Modul.
--
-- Bewusst flach: ein Stapel liegt in hoechstens einem Ordner, Ordner enthalten keine
-- Ordner. Fuer die Uebersicht ueber ein paar Dutzend Stapel reicht das.

CREATE TABLE folders (
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- Ein geloeschter Ordner nimmt die Stapel nicht mit; sie liegen danach wieder ausserhalb.
ALTER TABLE decks ADD COLUMN folder_id UUID REFERENCES folders (id) ON DELETE SET NULL;

CREATE INDEX idx_decks_folder ON decks (folder_id);
