package fi.poltsi.vempain.file.tools;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataToolITC {

	@Test
	void readsCodecFromEncodedVideoContainer(@TempDir Path tempDirectory) throws Exception {
		var video = tempDirectory.resolve("codec-sample.mp4");
		try (var recorder = new FFmpegFrameRecorder(video.toString(), 64, 48);
			 var converter = new Java2DFrameConverter()) {
			recorder.setFormat("mp4");
			recorder.setFrameRate(12);
			recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
			recorder.start();
			var image    = new BufferedImage(64, 48, BufferedImage.TYPE_3BYTE_BGR);
			var graphics = image.createGraphics();
			graphics.setColor(Color.GREEN);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.dispose();
			recorder.record(converter.convert(image));
			recorder.stop();
		}

		assertThat(MetadataTool.extractVideoCodec(video.toFile())).isEqualTo("mpeg4");
	}

	@Test
	void replacesSubjectsInAllExifToolLocations(@TempDir Path tempDirectory) throws Exception {
		var image = tempDirectory.resolve("subjects.jpg")
		                         .toFile();
		ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);

		MetadataTool.writeSubjects(image, java.util.List.of("2. maailmansota", "Kanada"));
		MetadataTool.writeSubjects(image, java.util.List.of("toinen maailmansota", "Kanada"));

		var metadata = MetadataTool.extractMetadataJsonObject(image);
		assertThat(MetadataTool.extractSubjects(metadata))
				.containsExactly("toinen maailmansota", "Kanada");
		assertThat(metadata.getJSONObject("XMP-dc")
		                   .getJSONArray("Subject")
		                   .toList())
				.containsExactly("toinen maailmansota", "Kanada");
		assertThat(metadata.getJSONObject("XMP-lr")
		                   .getJSONArray("HierarchicalSubject")
		                   .toList())
				.containsExactly("toinen maailmansota", "Kanada");
		assertThat(metadata.getJSONObject("XMP-lr")
		                   .getJSONArray("WeightedFlatSubject")
		                   .toList())
				.containsExactly("toinen maailmansota", "Kanada");
		assertThat(metadata.getJSONObject("IPTC")
		                   .getJSONArray("Keywords")
		                   .toList())
				.containsExactly("toinen maailmansota", "Kanada");
	}
}
