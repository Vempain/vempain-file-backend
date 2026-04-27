package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.MusicFileResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a music file. Extends {@link AudioFileEntity} to inherit
 * audio-technical fields (duration, bit rate, sample rate, codec, channels) and
 * adds music-metadata fields (artist, album, track name, track number, genre).
 */
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "music_files")
@PrimaryKeyJoinColumn(name = "id")
public class MusicFileEntity extends AudioFileEntity {

	@Column(name = "artist")
	private String artist;

	@Column(name = "album_artist")
	private String albumArtist;

	@Column(name = "album")
	private String album;

	@Column(name = "year")
	private Integer year;

	@Column(name = "track_name")
	private String trackName;

	@Column(name = "track_number")
	private Integer trackNumber;

	@Column(name = "track_total")
	private Integer trackTotal;

	@Column(name = "genre")
	private String genre;

	@Override
	public MusicFileResponse toResponse() {
		MusicFileResponse response = new MusicFileResponse();

		// Populate common file fields
		populateBaseResponse(response);

		// Populate audio-technical fields
		response.setDuration(getDuration());
		response.setBitRate(getBitRate());
		response.setSampleRate(getSampleRate());
		response.setCodec(getCodec());
		response.setChannels(getChannels());

		// Populate music-specific fields
		response.setArtist(getArtist());
		response.setAlbumArtist(getAlbumArtist());
		response.setAlbum(getAlbum());
		response.setYear(getYear());
		response.setTrackName(getTrackName());
		response.setTrackNumber(getTrackNumber());
		response.setTrackTotal(getTrackTotal());
		response.setGenre(getGenre());

		return response;
	}
}
