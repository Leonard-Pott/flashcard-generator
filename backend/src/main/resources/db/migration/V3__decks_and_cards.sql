-- V3: Stapel und Karten.

CREATE TABLE decks (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    language    VARCHAR(8)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE cards (
    id             UUID PRIMARY KEY,
    deck_id        UUID NOT NULL REFERENCES decks (id) ON DELETE CASCADE,
    front          TEXT NOT NULL,
    back           TEXT NOT NULL,

    -- Herkunft der Karte. Wird das Dokument geloescht, bleibt die Karte erhalten
    -- und verliert nur die Rueckverfolgbarkeit.
    source_document_id UUID REFERENCES documents (id) ON DELETE SET NULL,
    source_excerpt     TEXT,

    -- SM-2, ausgewertet ab M6.
    ease_factor    DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days  INTEGER          NOT NULL DEFAULT 0,
    repetitions    INTEGER          NOT NULL DEFAULT 0,
    due_date       DATE             NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_cards_deck ON cards (deck_id);
CREATE INDEX idx_cards_due ON cards (deck_id, due_date);

CREATE TABLE card_tags (
    card_id UUID        NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    tag     VARCHAR(64) NOT NULL,
    PRIMARY KEY (card_id, tag)
);
