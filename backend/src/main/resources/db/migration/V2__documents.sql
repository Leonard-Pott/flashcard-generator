-- V2: Hochgeladene Dokumente.
-- Gespeichert wird nur der extrahierte Text, nicht die Originaldatei.

CREATE TABLE documents (
    id             UUID PRIMARY KEY,
    filename       VARCHAR(512) NOT NULL,
    mime_type      VARCHAR(255) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL,
    extracted_text TEXT,
    char_count     INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(32)  NOT NULL,
    error_message  TEXT
);

CREATE INDEX idx_documents_uploaded_at ON documents (uploaded_at DESC);
