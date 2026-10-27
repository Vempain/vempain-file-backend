package fi.poltsi.vempain.file.tools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.poltsi.vempain.common.metadata.MetadataJson;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-type fields of every file type are read from the one grouped ExifTool JSON of a real file (through the shared {@code ExifToolService}) and the video
 * codec from the bundled FFmpeg; no file type needs a second ExifTool run.
 */
class FileMetadataToolITC {

    private static org.json.JSONObject metadata(java.io.File file) throws java.io.IOException {
        return TestExifTool.service()
            .readMetadata(file);
    }

    @Test
    void readsCodecFromEncodedVideoContainer(@TempDir Path tempDirectory) throws Exception {
        var video = tempDirectory.resolve("codec-sample.mp4");
        try (var recorder = new FFmpegFrameRecorder(video.toString(), 64, 48); var converter = new Java2DFrameConverter()) {
            recorder.setFormat("mp4");
            recorder.setFrameRate(12);
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
            recorder.start();
            var image = new BufferedImage(64, 48, BufferedImage.TYPE_3BYTE_BGR);
            var graphics = image.createGraphics();
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.dispose();
            recorder.record(converter.convert(image));
            recorder.stop();
        }

        assertThat(FileMetadataTool.extractVideoCodec(video.toFile())).isEqualTo("mpeg4");
    }

    @Test
    void readsAudioAndVideoPropertiesFromRealFiles(@TempDir Path tempDirectory) throws Exception {
        var video = metadata(SampleFiles.mp4(tempDirectory.resolve("clip.mp4")));
        assertThat(MetadataJson.extractFrameRate(video)).isEqualTo(12.0);
        assertThat(MetadataJson.extractAudioVideoDuration(video)
            .toMillis()).isGreaterThan(0L);
        assertThat(MetadataJson.extractXYResolution(video)).isEqualTo(new Dimension(64, 48));

        var wavFile = SampleFiles.wav(tempDirectory.resolve("tone.wav"));
        var wav = metadata(wavFile);
        assertThat(MetadataJson.extractAudioVideoDuration(wav)).isEqualTo(Duration.ofSeconds(2));
        assertThat(MetadataJson.extractAudioSampleRate(wav)).isEqualTo(8000);
        // No bit rate tag: it is computed from the file size and the duration
        assertThat(MetadataJson.extractAudioBitRate(wav, wavFile.length())).isGreaterThan(0);
        // WAV files carry neither an audio codec nor a channel count tag under the names the scanner reads
        assertThat(MetadataJson.extractAudioCodec(wav)).isEmpty();
        assertThat(MetadataJson.extractAudioChannels(wav)).isZero();

        var mp3 = tempDirectory.resolve("track.mp3");
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/test_mp3_id3v23.mp3"))) {
            Files.write(mp3, stream.readAllBytes());
        }
        var music = metadata(mp3.toFile());
        assertThat(MetadataJson.extractAudioBitRate(music, mp3.toFile()
            .length())).isGreaterThan(0);
        assertThat(MetadataJson.extractAudioSampleRate(music)).isGreaterThan(0);
        assertThat(MetadataJson.hasMusicMetadata(music)).isTrue();
    }

    @Test
    void readsTheAudioTrackOfAVideo(@TempDir Path tempDirectory) throws Exception {
        var video = metadata(SampleFiles.mp4WithAudio(tempDirectory.resolve("talk.mp4")));

        assertThat(MetadataJson.extractAudioSampleRate(video)).isEqualTo(8000);
        assertThat(MetadataJson.extractAudioChannels(video)).isEqualTo(1);
        assertThat(MetadataJson.extractAudioCodec(video)).isNotNull();
    }

    @Test
    void readsDocumentIconVectorFontAndArchiveTagsFromRealFiles(@TempDir Path tempDirectory) throws Exception {
        var pdf = metadata(SampleFiles.pdf(tempDirectory.resolve("doc.pdf")));
        assertThat(MetadataJson.extractDocumentPageCount(pdf)).isEqualTo(1);
        assertThat(MetadataJson.extractDocumentFormat(pdf)).isEqualTo("PDF");

        var icon = metadata(SampleFiles.ico(tempDirectory.resolve("icon.ico")));
        assertThat(MetadataJson.extractXYResolution(icon)).isEqualTo(new Dimension(16, 16));
        assertThat(MetadataJson.extractIconIsScalable(icon)).isFalse();

        var svg = metadata(SampleFiles.svg(tempDirectory.resolve("vector.svg")));
        assertThat(MetadataJson.extractXYResolution(svg)).isEqualTo(new Dimension(64, 32));
        // ExifTool reports no layer count for SVG
        assertThat(MetadataJson.extractVectorLayersCount(svg)).isZero();

        var gzip = metadata(SampleFiles.gzip(tempDirectory.resolve("text.gz")));
        assertThat(MetadataJson.extractArchiveCompressionMethod(gzip)).isEqualTo("Deflated");
        assertThat(MetadataJson.extractArchiveIsEncrypted(gzip)).isFalse();
        // ExifTool knows no uncompressed size or content count for gzip
        assertThat(MetadataJson.extractArchiveUncompressedSize(gzip)).isZero();
        assertThat(MetadataJson.extractArchiveContentCount(gzip)).isZero();

        var font = SampleFiles.systemFont();
        Assumptions.assumeTrue(font.isPresent(), "No TrueType font installed on this host");
        var ttf = tempDirectory.resolve("font.ttf");
        Files.copy(font.get(), ttf);
        var fontMetadata = metadata(ttf.toFile());
        assertThat(MetadataJson.extractFontFamily(fontMetadata)).isNotBlank();
        assertThat(MetadataJson.extractFontWeight(fontMetadata)).isNotNull();
        assertThat(MetadataJson.extractFontStyle(fontMetadata)).isNotNull();
    }

    @Test
    void missingFilesYieldNoMetadataAndEmptyValues(@TempDir Path tempDirectory) throws Exception {
        var missing = metadata(tempDirectory.resolve("missing.mp4")
            .toFile());

        assertThat(missing).isNull();
        assertThat(MetadataJson.extractFrameRate(missing)).isZero();
        assertThat(MetadataJson.extractXYResolution(missing)).isNull();
    }
}
