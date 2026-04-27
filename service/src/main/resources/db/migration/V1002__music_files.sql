-- Music files table: stores music-specific metadata for audio files that have
-- artist/album/track information. Inherits from audio_files via JOINED strategy.
CREATE TABLE music_files
(
    id           BIGINT PRIMARY KEY,
    artist       VARCHAR(255),
    album_artist VARCHAR(255),
    album        VARCHAR(255),
    year         INT,
    track_name   VARCHAR(255),
    track_number INT,
    track_total  INT,
    genre        VARCHAR(100),
    FOREIGN KEY (id) REFERENCES audio_files (id) ON DELETE CASCADE
);

CREATE INDEX idx_music_files_artist ON music_files (artist);
CREATE INDEX idx_music_files_album_artist ON music_files (album_artist);
CREATE INDEX idx_music_files_album ON music_files (album);
CREATE INDEX idx_music_files_year ON music_files (year);
CREATE INDEX idx_music_files_genre ON music_files (genre);
