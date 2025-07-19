-- Begin with the creation of the database schema for the authentication and access control list (ACL) handling
CREATE TABLE user_account
(
	id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	acl_id         BIGINT       NOT NULL UNIQUE,
	locked         BOOLEAN      NOT NULL DEFAULT false,
	birthday       TIMESTAMP    NOT NULL,
	description    VARCHAR(255),
	email          VARCHAR(255) NOT NULL UNIQUE,
	login_name     VARCHAR(255) NOT NULL UNIQUE,
	name           VARCHAR(255) NOT NULL,
	nick           VARCHAR(255) NOT NULL,
	password       VARCHAR(255) NOT NULL,
	priv_type      VARCHAR(10)  NOT NULL CHECK (priv_type IN ('PRIVATE', 'GROUP', 'PUBLIC')),
	public_account BOOLEAN      NOT NULL DEFAULT false,
	street         VARCHAR(255)          DEFAULT NULL,
	pob            VARCHAR(255)          DEFAULT NULL,
	status         VARCHAR(10)  NOT NULL CHECK (status IN ('REGISTERED', 'ACTIVE', 'DISABLED')),
	creator        BIGINT       NOT NULL,
	created        TIMESTAMP    NOT NULL,
	modifier       BIGINT,
	modified       TIMESTAMP,
	FOREIGN KEY (creator) REFERENCES user_account (id),
	FOREIGN KEY (modifier) REFERENCES user_account (id)
);

CREATE TABLE acl
(
	id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	acl_id           BIGINT  NOT NULL,
	create_privilege BOOLEAN NOT NULL,
	delete_privilege BOOLEAN NOT NULL,
	modify_privilege BOOLEAN NOT NULL,
	read_privilege   BOOLEAN NOT NULL,
	unit_id          BIGINT,
	user_id          BIGINT
);

-- Only either unit_id or user_id is not null
ALTER TABLE acl
	ADD CONSTRAINT acl_unit_user_xor CHECK ((unit_id IS NOT NULL AND user_id IS NULL) OR (unit_id IS NULL AND user_id IS NOT NULL));

-- There can be no two rows with the same acl_id, user_id or unit_id
ALTER TABLE acl
	ADD CONSTRAINT acl_unique_acl_id_user_id_unit_id UNIQUE (acl_id, user_id, unit_id);


CREATE TABLE unit
(
	id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	acl_id      BIGINT       NOT NULL UNIQUE,
	description VARCHAR(255),
	name        VARCHAR(255) NOT NULL UNIQUE,
	locked      BOOLEAN      NOT NULL DEFAULT false,
	creator     BIGINT       NOT NULL,
	created     TIMESTAMP    NOT NULL,
	modifier    BIGINT,
	modified    TIMESTAMP,
	FOREIGN KEY (creator) REFERENCES user_account (id),
	FOREIGN KEY (modifier) REFERENCES user_account (id)
);

CREATE TABLE user_unit
(
	id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	user_id BIGINT NOT NULL,
	unit_id BIGINT NOT NULL,
	FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
	FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE CASCADE
);

-- End of authentication ACL handling

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

-- Data

-- Add administrator account
INSERT INTO user_account (id, acl_id, birthday, created, creator, locked, email, login_name, name, nick, password, priv_type, public_account, street, status)
	OVERRIDING SYSTEM VALUE
VALUES (1, 1, '1900-01-01 00:00:00', NOW(), 1, false, 'admin@nohost.nodomain', 'admin', 'Vempain Administrator', 'Admin', 'Disabled', 'PRIVATE', false, '',
		'ACTIVE');

SELECT setval('user_account_id_seq', (SELECT MAX(id) + 1 FROM user_account));
