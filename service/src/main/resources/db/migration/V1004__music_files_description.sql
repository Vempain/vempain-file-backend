-- Add description column to music_files table to store the Vorbis Description
-- field (FLAC) or ID3 Comment field (MP3) from the audio metadata.
ALTER TABLE music_files
	ADD COLUMN description VARCHAR(1024);

