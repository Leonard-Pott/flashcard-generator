-- V4: Generierungslaeufe.

CREATE TABLE generation_jobs (
    id                   UUID PRIMARY KEY,
    status               VARCHAR(16) NOT NULL,
    target_deck_id       UUID REFERENCES decks (id) ON DELETE SET NULL,
    language             VARCHAR(8)  NOT NULL,
    style                VARCHAR(16) NOT NULL,
    cards_per_chunk      INTEGER     NOT NULL,

    total_chunks         INTEGER     NOT NULL DEFAULT 0,
    processed_chunks     INTEGER     NOT NULL DEFAULT 0,
    skipped_chunks       INTEGER     NOT NULL DEFAULT 0,
    generated_card_count INTEGER     NOT NULL DEFAULT 0,

    -- Welches Modell den Lauf verarbeitet hat; hilfreich beim Vergleich nach Modellwechseln.
    llm_model            VARCHAR(64),
    error_message        TEXT,

    created_at           TIMESTAMPTZ NOT NULL,
    finished_at          TIMESTAMPTZ
);

-- Bewusst ohne Fremdschluessel: der Job ist ein Protokoll und soll auch dann noch
-- lesbar sein, wenn ein Quelldokument spaeter geloescht wurde.
CREATE TABLE generation_job_documents (
    job_id      UUID    NOT NULL REFERENCES generation_jobs (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    document_id UUID    NOT NULL,
    PRIMARY KEY (job_id, position)
);

CREATE INDEX idx_generation_jobs_created_at ON generation_jobs (created_at DESC);
