-- Dify Knowledge Base chapter-level sync tracking
-- Run this migration to enable per-chapter KB synchronization
ALTER TABLE sys_chapter
    ADD COLUMN kb_document_id VARCHAR(64) DEFAULT NULL COMMENT 'Dify KB document ID, NULL if not yet indexed';

-- Cleanup: book-level kb_document_id is no longer needed (replaced by chapter-level tracking)
ALTER TABLE sys_book
    DROP COLUMN kb_document_id;
