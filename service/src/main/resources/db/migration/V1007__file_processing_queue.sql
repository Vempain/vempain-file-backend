CREATE TABLE file_processing_queue
(
	id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	file_id       BIGINT      NOT NULL UNIQUE,
	file_type     VARCHAR(32) NOT NULL,
	status        VARCHAR(16) NOT NULL,
	attempts      INT         NOT NULL DEFAULT 0,
	created       TIMESTAMP   NOT NULL,
	updated       TIMESTAMP   NOT NULL,
	started       TIMESTAMP,
	completed     TIMESTAMP,
	error_message TEXT,
	CONSTRAINT fk_file_processing_queue_file
		FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE INDEX idx_file_processing_queue_status_created
	ON file_processing_queue (status, created);
