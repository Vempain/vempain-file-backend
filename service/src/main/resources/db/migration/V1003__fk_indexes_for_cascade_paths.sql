-- Add missing indexes on foreign keys used in cascade paths.
-- These improve delete performance when removing parent rows from files/tags.

CREATE INDEX idx_file_group_files_file_id ON file_group_files (file_id);
CREATE INDEX idx_metadata_file_id ON metadata (file_id);
CREATE INDEX idx_export_files_file_id ON export_files (file_id);
CREATE INDEX idx_file_tags_tag_id ON file_tags (tag_id);

