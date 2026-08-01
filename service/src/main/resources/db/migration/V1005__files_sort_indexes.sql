-- Indexes supporting the paged file listings, which ORDER BY columns of the parent `files` table.
--
-- Symptom: the FIRST page of `POST /api/files/<type>/paged` (page index 0) takes several seconds on a
-- large library (e.g. ~76 000 rows in image_files), while every subsequent page returns in ~130 ms.
--
-- Root cause: the only pre-existing index touching `filename` is the composite UNIQUE constraint
-- (file_path, filename). Because its leading column is `file_path`, PostgreSQL cannot use it to satisfy
-- `ORDER BY filename`, so the paged query falls back to a full sequential scan + in-memory sort of the
-- WIDE `files` table (which also carries the potentially large `metadata_raw`/`description` TEXT columns)
-- on every request. When the table is cold, that first scan reads all heap pages from disk (several
-- seconds); the immediately following pages hit the OS/DB page cache and are fast -- exactly the
-- "page 0 slow / page 1 fast" pattern. It reappears whenever the cache is evicted.
--
-- Fix: add plain btree indexes on the sortable `files` columns so the paged lookup becomes an ordered
-- index scan that only touches the requested page instead of scanning and sorting the whole table.
-- (`id` is covered by the primary key; `file_path` by the composite UNIQUE constraint above.)

CREATE INDEX IF NOT EXISTS idx_files_filename ON files (filename);
CREATE INDEX IF NOT EXISTS idx_files_mimetype ON files (mimetype);
CREATE INDEX IF NOT EXISTS idx_files_filesize ON files (filesize);
CREATE INDEX IF NOT EXISTS idx_files_created ON files (created);
CREATE INDEX IF NOT EXISTS idx_files_modified ON files (modified);
