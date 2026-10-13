-- Descriptions were only read from the family 0 "XMP" group, which the scanner's "exiftool -g1" output never contains, so files
-- scanned so far carry no description unless they had an IPTC caption. The metadata table holds the "-g1" rows of every scanned
-- file, so the XMP-dc:Description and IFD0:ImageDescription values can be backfilled from there (XMP-dc preferred).
UPDATE files f
SET description = m.metadata_value
FROM metadata m
WHERE m.file_id = f.id
  AND m.metadata_group = 'XMP-dc'
  AND m.metadata_key = 'Description'
  AND m.metadata_value <> ''
  AND (f.description IS NULL OR f.description = '');

UPDATE files f
SET description = m.metadata_value
FROM metadata m
WHERE m.file_id = f.id
  AND m.metadata_group = 'IFD0'
  AND m.metadata_key = 'ImageDescription'
  AND m.metadata_value <> ''
  AND (f.description IS NULL OR f.description = '');

-- Thumbnails used to store their pixel size ("<width>x<height>") as their description; they describe the same content as the
-- file they were generated from, so they carry that file's description from now on.
UPDATE files f
SET description = original.description
FROM thumb_files tf
		 JOIN files original ON original.id = tf.target_file_id
WHERE f.id = tf.id
  AND (f.description IS NULL OR f.description ~ '^[0-9]+x[0-9]+$');
