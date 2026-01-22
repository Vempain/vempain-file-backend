CREATE TABLE file_group
(
	id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	path       VARCHAR(255) NOT NULL,
	group_name  VARCHAR(255) NOT NULL,
	gallery_id BIGINT DEFAULT NULL,
	description TEXT
);

CREATE TABLE files
(
	id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	acl_id                   BIGINT       NOT NULL UNIQUE,
	external_file_id         VARCHAR(255) NOT NULL,
	filename                 VARCHAR(255) NOT NULL,
	file_path       VARCHAR(255) NOT NULL,
	mimetype                 VARCHAR(255) NOT NULL,
	filesize                 BIGINT       NOT NULL,
	original_datetime        TIMESTAMP,
	original_second_fraction INT,
	original_document_id     VARCHAR(128),
	sha256sum                VARCHAR(64)  NOT NULL,
	file_type                VARCHAR(50)  NOT NULL,
	description              TEXT,
	metadata_raw             TEXT,
	rights_holder   VARCHAR(255),
	rights_terms    VARCHAR(255),
	rights_url      VARCHAR(255),
	creator_name    VARCHAR(255),
	creator_email   VARCHAR(255),
	creator_country VARCHAR(128),
	creator_url     VARCHAR(255),
	gps_timestamp   TIMESTAMP,
	gps_location_id BIGINT,
	creator                  BIGINT       NOT NULL,
	created                  TIMESTAMP    NOT NULL,
	modifier                 BIGINT,
	modified                 TIMESTAMP,
	locked                   BOOLEAN      NOT NULL DEFAULT false,
	FOREIGN KEY (creator) REFERENCES user_account (id),
	FOREIGN KEY (modifier) REFERENCES user_account (id),
	CONSTRAINT uq_files_file_path_filename UNIQUE (file_path, filename)
);

CREATE TABLE file_group_files
(
	file_group_id BIGINT NOT NULL,
	file_id       BIGINT NOT NULL,
	CONSTRAINT pk_file_group_files PRIMARY KEY (file_group_id, file_id),
	CONSTRAINT fk_fgf_group FOREIGN KEY (file_group_id) REFERENCES file_group (id) ON DELETE CASCADE,
	CONSTRAINT fk_fgf_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
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
	tag_name    VARCHAR(256) UNIQUE NOT NULL,
	tag_name_de VARCHAR(256)        NULL,
	tag_name_en VARCHAR(256)        NULL,
	tag_name_es VARCHAR(256)        NULL,
	tag_name_fi VARCHAR(256)        NULL,
	tag_name_sv VARCHAR(256)        NULL
);

CREATE TABLE file_tags
(
	file_id BIGINT NOT NULL,
	tag_id  BIGINT NOT NULL,
	CONSTRAINT pk_file_tags PRIMARY KEY (file_id, tag_id),
	CONSTRAINT fk_file_tags_files FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
	CONSTRAINT fk_file_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE image_files
(
	id          BIGINT PRIMARY KEY,
	width       INT NOT NULL,
	height      INT NOT NULL,
	color_depth INT NOT NULL,
	dpi         INT NOT NULL,
	group_label VARCHAR(64),
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE video_files
(
	id         BIGINT PRIMARY KEY,
	width      INT              NOT NULL,
	height     INT              NOT NULL,
	frame_rate DOUBLE PRECISION NOT NULL,
	duration NUMERIC(5, 0) NOT NULL,
	codec      VARCHAR(100)     NOT NULL,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE audio_files
(
	id          BIGINT PRIMARY KEY,
	duration    NUMERIC(5, 0) NOT NULL,
	bit_rate    INT           NOT NULL,
	sample_rate INT           NOT NULL,
	codec       VARCHAR(100)  NOT NULL,
	channels    INT           NOT NULL,
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

CREATE TABLE binary_files
(
	id                     BIGINT PRIMARY KEY,
	software_name          VARCHAR(255),
	software_major_version INT,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE data_files
(
	id             BIGINT PRIMARY KEY,
	data_structure VARCHAR(50),
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE executable_files
(
	id        BIGINT PRIMARY KEY,
	is_script BOOLEAN NOT NULL DEFAULT false,
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE executable_os
(
	file_id BIGINT      NOT NULL,
	os      VARCHAR(50) NOT NULL,
	PRIMARY KEY (file_id, os),
	FOREIGN KEY (file_id) REFERENCES executable_files (id) ON DELETE CASCADE
);

CREATE TABLE interactive_files
(
	id         BIGINT PRIMARY KEY,
	technology VARCHAR(50),
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE thumb_files
(
	id             BIGINT PRIMARY KEY,
	target_file_id BIGINT,
	relation_type  VARCHAR(50),
	FOREIGN KEY (id) REFERENCES files (id) ON DELETE CASCADE,
	FOREIGN KEY (target_file_id) REFERENCES files (id) ON DELETE SET NULL
);

CREATE INDEX idx_binary_files_software_name ON binary_files (software_name);
CREATE INDEX idx_data_files_structure ON data_files (data_structure);
CREATE INDEX idx_executable_files_is_script ON executable_files (is_script);
CREATE INDEX idx_interactive_files_technology ON interactive_files (technology);
CREATE INDEX idx_thumb_files_target ON thumb_files (target_file_id);

CREATE TABLE export_files
(
	id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	file_id              BIGINT       NOT NULL,
	filename             VARCHAR(255) NOT NULL,
	file_path            VARCHAR(255) NOT NULL,
	mimetype             VARCHAR(255) NOT NULL,
	filesize             BIGINT       NOT NULL,
	sha256sum            VARCHAR(64)  NOT NULL,
	original_document_id VARCHAR(128),
	created              TIMESTAMP    NOT NULL,
	FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);

CREATE TABLE gps_locations
(
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	latitude      DECIMAL(15, 5) NOT NULL,
	latitude_ref  CHAR(1)        NOT NULL,
	longitude     DECIMAL(15, 5) NOT NULL,
	longitude_ref CHAR(1)        NOT NULL,
	altitude        DOUBLE PRECISION,
	direction       DOUBLE PRECISION,
	satellite_count INT,
	country         VARCHAR(128),
	state           VARCHAR(128),
	city            VARCHAR(128),
	street          VARCHAR(255),
	sub_location    VARCHAR(255)
);

CREATE TABLE location_guard
(
	id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	guard_type          VARCHAR(16)    NOT NULL,
	primary_longitude   NUMERIC(10, 5) NOT NULL,
	primary_latitude    NUMERIC(10, 5) NOT NULL,
	secondary_longitude NUMERIC(10, 5),
	secondary_latitude  NUMERIC(10, 5),
	radius              NUMERIC(10, 5)
);

-- Data
-- Add administrator account
INSERT INTO user_account (id, acl_id, birthday, created, creator, locked, email, login_name, name, nick, password, priv_type, public_account, street, status)
	OVERRIDING SYSTEM VALUE
VALUES (1, 1, '1900-01-01 00:00:00', NOW(), 1, false, 'admin@nohost.nodomain', 'admin', 'Vempain Administrator', 'Admin', 'Disabled', 'PRIVATE', false, '',
		'ACTIVE');
INSERT INTO acl (id, acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege)
	OVERRIDING SYSTEM VALUE
VALUES (1, 1, 1, null, true, true, true, true);

SELECT setval('user_account_id_seq', (SELECT MAX(id) + 1 FROM user_account));

-- Default Site Style file
INSERT INTO acl (id, acl_id, user_id, unit_id, create_privilege, read_privilege, modify_privilege, delete_privilege)
	OVERRIDING SYSTEM VALUE
VALUES (2, 1000, 1, null, true, true, true, true);

INSERT INTO files (id, acl_id, external_file_id, filename, file_path, mimetype, filesize, sha256sum, file_type, creator, created, locked)
	OVERRIDING SYSTEM VALUE
VALUES (1, 1000, 'default-site-style', 'default-style.json', '/document/site', 'text/json', 0,
		'fea66b008bcd1753c1ede5711e42b4274b956761c9e386a61450b7ebfe250a5f', 'DOCUMENT', 1, NOW(), false);

INSERT INTO document_files (id, page_count, format)
	OVERRIDING SYSTEM VALUE
VALUES (1, 0, 'JSON');

SELECT setval('acl_id_seq', 2000);
SELECT setval('files_id_seq', 1000);
