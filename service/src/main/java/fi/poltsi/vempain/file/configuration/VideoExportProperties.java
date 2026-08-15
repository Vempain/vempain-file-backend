package fi.poltsi.vempain.file.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class VideoExportProperties {
	@Value("${vempain.file-processing.video.width:1280}")
	private int    width;
	@Value("${vempain.file-processing.video.height:720}")
	private int    height;
	@Value("${vempain.file-processing.video.fps:30}")
	private double fps;
	@Value("${vempain.file-processing.video.audio-bitrate:128000}")
	private int    audioBitrate;
	@Value("${vempain.file-processing.video.audio-codec:vorbis}")
	private String audioCodec;
	@Value("${vempain.file-processing.video.video-codec:mpeg4}")
	private String videoCodec;
	@Value("${vempain.file-processing.video.container:mp4}")
	private String container;
	@Value("${vempain.file-processing.video.quality:23}")
	private int    quality;
}
