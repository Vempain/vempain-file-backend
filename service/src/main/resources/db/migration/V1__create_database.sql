CREATE TABLE file_group
(
	id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	path       VARCHAR(255) NOT NULL,
	group_name VARCHAR(255) NOT NULL
);

CREATE TABLE files
(
	id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	external_file_id VARCHAR(255) NOT NULL,
	file_group_id    BIGINT       NOT NULL,
	filename         VARCHAR(255) NOT NULL,
	mimetype         VARCHAR(255) NOT NULL,
	filesize         BIGINT       NOT NULL,
	sha256sum        VARCHAR(64)  NOT NULL,
	created_at       TIMESTAMP    NOT NULL,
	file_type        VARCHAR(50)  NOT NULL,
	CONSTRAINT fk_files_file_group_id FOREIGN KEY (file_group_id) REFERENCES file_group (id) ON DELETE CASCADE
);

CREATE TABLE metadata
(
	id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	file_id        BIGINT       NOT NULL,
	metadata_group VARCHAR(128) NOT NULL,
	metadata_key   VARCHAR(128) NOT NULL,
	metadata_value TEXT         NOT NULL,
	CONSTRAINT fk_metadata_files FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE tags
(
	id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	tag_name    VARCHAR(128) UNIQUE NOT NULL,
	tag_name_de VARCHAR(128) UNIQUE NOT NULL,
	tag_name_en VARCHAR(128) UNIQUE NOT NULL,
	tag_name_es VARCHAR(128) UNIQUE NOT NULL,
	tag_name_fi VARCHAR(128) UNIQUE NOT NULL,
	tag_name_sv VARCHAR(128) UNIQUE NOT NULL
);

CREATE TABLE file_tags
(
	file_id BIGINT NOT NULL,
	tag_id  BIGINT NOT NULL,
	CONSTRAINT pk_file_tags PRIMARY KEY (file_id, tag_id),
	CONSTRAINT fk_file_tags_files FOREIGN KEY (file_id) REFERENCES files (id),
	CONSTRAINT fk_file_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE image_files
(
	id          BIGINT PRIMARY KEY,
	width       INT NOT NULL,
	height      INT NOT NULL,
	color_depth INT NOT NULL,
	dpi         INT NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE video_files
(
	id         BIGINT PRIMARY KEY,
	width      INT              NOT NULL,
	height     INT              NOT NULL,
	frame_rate DOUBLE PRECISION NOT NULL,
	duration   DOUBLE PRECISION NOT NULL,
	codec      VARCHAR(100)     NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE audio_files
(
	id          BIGINT PRIMARY KEY,
	duration    DOUBLE PRECISION NOT NULL,
	bit_rate    INT              NOT NULL,
	sample_rate INT              NOT NULL,
	codec       VARCHAR(100)     NOT NULL,
	channels    INT              NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE document_files
(
	id         BIGINT PRIMARY KEY,
	page_count INT         NOT NULL,
	format     VARCHAR(50) NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE vector_files
(
	id           BIGINT PRIMARY KEY,
	width        INT NOT NULL,
	height       INT NOT NULL,
	layers_count INT NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE icon_files
(
	id          BIGINT PRIMARY KEY,
	width       INT NOT NULL,
	height      INT NOT NULL,
	is_scalable BOOLEAN DEFAULT FALSE,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE font_files
(
	id          BIGINT PRIMARY KEY,
	font_family VARCHAR(100) NOT NULL,
	weight      VARCHAR(50)  NOT NULL,
	style       VARCHAR(50)  NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE archive_files
(
	id                 BIGINT PRIMARY KEY,
	compression_method VARCHAR(50) NOT NULL,
	uncompressed_size  BIGINT      NOT NULL,
	content_count      INT         NOT NULL,
	is_encrypted       BOOLEAN DEFAULT FALSE,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);
