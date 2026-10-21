-- The site file a publish created or updated in the admin backend, recorded from the ingest answer so that the next publish of the same
-- file can read back the ACL grantees of the previous one. NULL until the file is published by a build carrying this column.
ALTER TABLE files
    ADD COLUMN site_file_id BIGINT;
