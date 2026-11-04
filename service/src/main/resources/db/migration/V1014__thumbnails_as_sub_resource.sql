CREATE TABLE file_thumbnails
(
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id   BIGINT       NOT NULL,
    filename  VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    mimetype  VARCHAR(255) NOT NULL,
    filesize  BIGINT       NOT NULL,
    sha256sum VARCHAR(64)  NOT NULL,
    width     INT,
    height    INT,
    created   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_file_thumbnails_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT uq_file_thumbnails_file UNIQUE (file_id),
    CONSTRAINT uq_file_thumbnails_path UNIQUE (file_path, filename)
);

INSERT INTO image_files (id, width, height, color_depth, dpi, group_label)
SELECT f.id, NULL, NULL, NULL, NULL, NULL
FROM files f
         JOIN thumb_files t ON t.id = f.id
WHERE f.file_path NOT LIKE '/thumb'
  AND f.file_path NOT LIKE '/thumb/%';

UPDATE files f
SET file_type = 'IMAGE'
FROM thumb_files t
WHERE t.id = f.id
  AND f.file_path NOT LIKE '/thumb'
  AND f.file_path NOT LIKE '/thumb/%';

DELETE
FROM thumb_files t
    USING files f
WHERE f.id = t.id
  AND f.file_type = 'IMAGE';

INSERT INTO file_thumbnails (file_id, filename, file_path, mimetype, filesize, sha256sum, width, height, created)
SELECT t.target_file_id,
       f.filename,
       f.file_path,
       f.mimetype,
       f.filesize,
       f.sha256sum,
       NULL,
       NULL,
       f.created
FROM thumb_files t
         JOIN files f ON f.id = t.id
WHERE t.target_file_id IS NOT NULL;

CREATE TABLE legacy_thumbnail_orphans
(
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_path VARCHAR(255) NOT NULL,
    filename  VARCHAR(255) NOT NULL,
    sha256sum VARCHAR(64)  NOT NULL,
    recorded  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO legacy_thumbnail_orphans (file_path, filename, sha256sum)
SELECT f.file_path, f.filename, f.sha256sum
FROM thumb_files t
         JOIN files f ON f.id = t.id
WHERE t.target_file_id IS NULL;

DELETE
FROM acl
WHERE acl_id IN (SELECT f.acl_id
                 FROM files f
                          JOIN thumb_files t ON t.id = f.id);

DELETE
FROM files
WHERE id IN (SELECT id FROM thumb_files);

DROP TABLE thumb_files;
