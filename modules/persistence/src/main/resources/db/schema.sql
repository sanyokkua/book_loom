-- BookLoom storage schema (ADR-0043). Until the first release this is the only schema file: a database whose
-- user_version differs, or whose meta 'record_shapes' fingerprint differs from the stored records of the running
-- build (StoredShapes), is renamed aside and a fresh one is created. Documents are JSON; real columns exist only
-- where an index, a uniqueness rule or a cascade needs them.

CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    type  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS providers (
    id   TEXT PRIMARY KEY,
    json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS model_settings (
    provider_id TEXT NOT NULL,
    model_id    TEXT NOT NULL,
    json        TEXT NOT NULL,
    PRIMARY KEY (provider_id, model_id)
);

CREATE TABLE IF NOT EXISTS books (
    content_hash TEXT PRIMARY KEY,
    source_path  TEXT NOT NULL,
    format       TEXT NOT NULL,
    title        TEXT NOT NULL,
    json         TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS projects (
    id             TEXT PRIMARY KEY,
    content_hash   TEXT NOT NULL REFERENCES books (content_hash),
    name           TEXT NOT NULL,
    name_key       TEXT NOT NULL,
    name_origin    TEXT NOT NULL,
    source_lang    TEXT NOT NULL,
    target_lang    TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    last_opened_at TEXT NOT NULL,
    brief_json     TEXT NOT NULL,
    UNIQUE (content_hash, name_key)
);

CREATE INDEX IF NOT EXISTS idx_projects_book_langs ON projects (content_hash, source_lang, target_lang);

CREATE TABLE IF NOT EXISTS project_docs (
    project_id TEXT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    kind       TEXT NOT NULL,
    json       TEXT NOT NULL,
    PRIMARY KEY (project_id, kind)
);

CREATE TABLE IF NOT EXISTS segments (
    project_id TEXT    NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    segment_id TEXT    NOT NULL,
    ord        INTEGER NOT NULL,
    unit_id    TEXT    NOT NULL,
    status     TEXT    NOT NULL,
    json       TEXT    NOT NULL,
    PRIMARY KEY (project_id, segment_id)
);

CREATE INDEX IF NOT EXISTS idx_segments_status ON segments (project_id, status);

CREATE TABLE IF NOT EXISTS tm_entries (
    project_id  TEXT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    source_hash TEXT NOT NULL,
    context_key TEXT NOT NULL,
    json        TEXT NOT NULL,
    PRIMARY KEY (project_id, source_hash, context_key)
);

CREATE TABLE IF NOT EXISTS deferrals (
    project_id TEXT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    segment_id TEXT NOT NULL,
    reason     TEXT NOT NULL,
    waiting_on TEXT NOT NULL,
    json       TEXT NOT NULL,
    PRIMARY KEY (project_id, segment_id, reason, waiting_on)
);

PRAGMA user_version = 2;
