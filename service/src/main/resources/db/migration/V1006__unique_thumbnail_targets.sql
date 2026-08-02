ALTER TABLE thumb_files
	ADD CONSTRAINT uq_thumb_files_target_file UNIQUE (target_file_id);
