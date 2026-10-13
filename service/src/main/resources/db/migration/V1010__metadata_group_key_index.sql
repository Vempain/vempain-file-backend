-- The metadata management view looks files up by ExifTool group and key (the value is a TEXT column and stays outside the
-- btree index). The scanner only ever reads the rows of one file (idx_metadata_file_id, V1003).
CREATE INDEX idx_metadata_group_key ON metadata (metadata_group, metadata_key);
