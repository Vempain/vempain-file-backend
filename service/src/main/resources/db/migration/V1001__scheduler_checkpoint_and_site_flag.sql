ALTER TABLE files
	ADD COLUMN site_file_published BOOLEAN;

CREATE TABLE scheduler_checkpoint
(
	task_name    VARCHAR(100) PRIMARY KEY,
	last_checked TIMESTAMP NOT NULL
);

