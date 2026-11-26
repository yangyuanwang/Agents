ALTER TABLE knowledge_base
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER updated_at;

-- Existing deleted_at values, if any, are retained. This column records the new explicit logical-delete state.
