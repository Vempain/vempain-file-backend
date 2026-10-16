package fi.poltsi.vempain.file.tools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataToolITC {

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

        assertThat(MetadataTool.extractVideoCodec(video.toFile())).isEqualTo("mpeg4");
    }

    @Test
    void writesOverwritesAndRemovesOneGroupKeyValue(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("value.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);

        MetadataTool.writeMetadataValue(image, "XMP-dc", "Creator", "Jane Doe");
        assertThat(MetadataTool.extractMetadataJsonObject(image)
            .getJSONObject("XMP-dc")
            .getString("Creator")).isEqualTo("Jane Doe");

        MetadataTool.writeMetadataValue(image, "XMP-dc", "Creator", "Jane A. Doe");
        assertThat(MetadataTool.extractMetadataJsonObject(image)
            .getJSONObject("XMP-dc")
            .getString("Creator")).isEqualTo("Jane A. Doe");

        MetadataTool.writeMetadataValue(image, "XMP-dc", "Creator", "");
        var afterRemoval = MetadataTool.extractMetadataJsonObject(image)
            .optJSONObject("XMP-dc");
        assertThat(afterRemoval == null
                   || !afterRemoval.has("Creator"))
            .isTrue();
    }

    @Test
    void extractsTheDescriptionWrittenByExifTool(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("described.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);

        MetadataTool.writeMetadataValue(image, "IFD0", "ImageDescription", "Written into IFD0");
        assertThat(MetadataTool.extractDescription(MetadataTool.extractMetadataJsonObject(image))).isEqualTo("Written into IFD0");

        MetadataTool.writeMetadataValue(image, "XMP-dc", "Description", "Written into XMP-dc");
        assertThat(MetadataTool.extractDescription(MetadataTool.extractMetadataJsonObject(image))).isEqualTo("Written into XMP-dc");
    }

    @Test
    void refusesUnsafeNamesAndReadOnlyGroups(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("refused.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);

        assertThat(MetadataTool.isWritableGroup("XMP-dc")).isTrue();
        assertThat(MetadataTool.isWritableGroup("Composite")).isFalse();
        assertThat(MetadataTool.isWritableGroup("File")).isFalse();
        assertThat(MetadataTool.isValidTagName("ID3v2_3")).isTrue();
        assertThat(MetadataTool.isValidTagName("-m")).isFalse();
        assertThat(MetadataTool.isValidTagName("Creator<")).isFalse();
        assertThat(MetadataTool.isValidTagName("Creator+")).isFalse();

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> MetadataTool.writeMetadataValue(image, "Composite", "ImageSize", "9x9"));
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> MetadataTool.writeMetadataValue(image, "XMP-dc", "Creator<", "x"));
        // ExifTool itself refuses tags it does not know
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> MetadataTool.writeMetadataValue(image, "XMP-dc", "NoSuchTagInExifTool", "x"));
    }

    @Test
    void replacesSubjectsInAllExifToolLocations(@TempDir Path tempDirectory) throws Exception {
        var image = tempDirectory.resolve("subjects.jpg")
            .toFile();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", image);

        MetadataTool.writeSubjects(image, java.util.List.of("2. maailmansota", "Kanada"));
        MetadataTool.writeSubjects(image, java.util.List.of("toinen maailmansota", "Kanada"));

        var metadata = MetadataTool.extractMetadataJsonObject(image);
        assertThat(MetadataTool.extractSubjects(metadata)).containsExactly("toinen maailmansota", "Kanada");
        assertThat(metadata.getJSONObject("XMP-dc")
            .getJSONArray("Subject")
            .toList()).containsExactly("toinen maailmansota", "Kanada");
        assertThat(metadata.getJSONObject("XMP-lr")
            .getJSONArray("HierarchicalSubject")
            .toList()).containsExactly("toinen maailmansota", "Kanada");
        assertThat(metadata.getJSONObject("XMP-lr")
            .getJSONArray("WeightedFlatSubject")
            .toList()).containsExactly("toinen maailmansota", "Kanada");
        assertThat(metadata.getJSONObject("IPTC")
            .getJSONArray("Keywords")
            .toList()).containsExactly("toinen maailmansota", "Kanada");

        // an empty list removes the subjects from every location instead of leaving the file untouched
        MetadataTool.writeSubjects(image, java.util.List.of());
        assertThat(MetadataTool.extractSubjects(MetadataTool.extractMetadataJsonObject(image))).isEmpty();
    }

    @Test
    void readsAudioAndVideoPropertiesFromRealFiles(@TempDir Path tempDirectory) throws Exception {
        var video = SampleFiles.mp4(tempDirectory.resolve("clip.mp4"));
        assertThat(MetadataTool.extractFrameRate(video)).isEqualTo(12.0);
        assertThat(MetadataTool.extractAudioVideoDuration(video)
            .toMillis()).isGreaterThan(0L);
        var resolution = MetadataTool.extractXYResolution(video);
        assertThat(resolution.width).isEqualTo(64);
        assertThat(resolution.height).isEqualTo(48);

        var wav = SampleFiles.wav(tempDirectory.resolve("tone.wav"));
        assertThat(MetadataTool.extractAudioVideoDuration(wav)).isEqualTo(java.time.Duration.ofSeconds(2));
        assertThat(MetadataTool.extractAudioSampleRate(wav)).isEqualTo(8000);
        // No bit rate tag: it is computed from the file size and the duration
        assertThat(MetadataTool.extractAudioBitRate(wav)).isGreaterThan(0);
        // WAV files carry neither an audio codec nor a channel count tag
        assertThat(MetadataTool.extractAudioCodec(wav)).isEmpty();
        assertThat(MetadataTool.extractAudioChannels(wav)).isZero();

        var mp3 = tempDirectory.resolve("track.mp3");
        try (var stream = java.util.Objects.requireNonNull(getClass().getResourceAsStream("/test_mp3_id3v23.mp3"))) {
            java.nio.file.Files.write(mp3, stream.readAllBytes());
        }
        assertThat(MetadataTool.extractAudioBitRate(mp3.toFile())).isGreaterThan(0);
        assertThat(MetadataTool.extractAudioSampleRate(mp3.toFile())).isGreaterThan(0);
    }

    @Test
    void readsDocumentIconVectorFontAndArchiveTagsFromRealFiles(@TempDir Path tempDirectory) throws Exception {
        var pdf = SampleFiles.pdf(tempDirectory.resolve("doc.pdf"));
        assertThat(MetadataTool.extractDocumentPageCount(pdf)).isEqualTo(1);
        assertThat(MetadataTool.extractDocumentFormat(pdf)).isEqualTo("PDF");

        var icon = SampleFiles.ico(tempDirectory.resolve("icon.ico"));
        assertThat(MetadataTool.extractXYResolution(icon)).isEqualTo(new java.awt.Dimension(16, 16));
        assertThat(MetadataTool.extractIconIsScalable(icon)).isFalse();

        var svg = SampleFiles.svg(tempDirectory.resolve("vector.svg"));
        assertThat(MetadataTool.extractXYResolution(svg)).isEqualTo(new java.awt.Dimension(64, 32));
        // ExifTool reports no layer count for SVG, and the empty value is not a number
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> MetadataTool.extractVectorLayersCount(svg));

        var gzip = SampleFiles.gzip(tempDirectory.resolve("text.gz"));
        assertThat(MetadataTool.extractArchiveCompressionMethod(gzip)).isEqualTo("Deflated");
        assertThat(MetadataTool.extractArchiveIsEncrypted(gzip)).isFalse();
        // ExifTool knows no uncompressed size or content count for gzip, so the empty values are not numbers either
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> MetadataTool.extractArchiveUncompressedSize(gzip));
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> MetadataTool.extractArchiveContentCount(gzip));

        var font = SampleFiles.systemFont();
        org.junit.jupiter.api.Assumptions.assumeTrue(font.isPresent(), "No TrueType font installed on this host");
        var ttf = tempDirectory.resolve("font.ttf");
        java.nio.file.Files.copy(font.get(), ttf);
        assertThat(MetadataTool.extractFontFamily(ttf.toFile())).isNotBlank();
        assertThat(MetadataTool.extractFontWeight(ttf.toFile())).isNotNull();
        assertThat(MetadataTool.extractFontStyle(ttf.toFile())).isNotNull();
    }

    @Test
    void copiesWritesFromJsonAndWipesMetadata(@TempDir Path tempDirectory) throws Exception {
        var source = SampleFiles.jpeg(tempDirectory.resolve("source.jpg"));
        MetadataTool.writeMetadataValue(source, "XMP-dc", "Creator", "Copied Creator");
        var destination = SampleFiles.jpeg(tempDirectory.resolve("destination.jpg"));

        MetadataTool.copyMetadata(source, destination);
        assertThat(MetadataTool.extractCreatorName(MetadataTool.extractMetadataJsonObject(destination))).isEqualTo("Copied Creator");
        // A missing destination makes ExifTool exit with an error, which is only logged
        MetadataTool.copyMetadata(source, tempDirectory.resolve("missing.jpg")
            .toFile());

        MetadataTool.writeMetadataFromJson(destination, "[{\"XMP-dc:Creator\":\"Json Creator\",\"XMP-dc:Description\":\"From JSON\"}]");
        var metadata = MetadataTool.extractMetadataJsonObject(destination);
        assertThat(MetadataTool.extractCreatorName(metadata)).isEqualTo("Json Creator");
        assertThat(MetadataTool.extractDescription(metadata)).isEqualTo("From JSON");

        MetadataTool.wipeMetadataFromFile(destination);
        assertThat(MetadataTool.extractCreatorName(MetadataTool.extractMetadataJsonObject(destination))).isNull();

        // Empty inputs and missing files are logged, never thrown
        MetadataTool.writeMetadataFromJson(null, "[{}]");
        MetadataTool.writeMetadataFromJson(destination, " ");
        var missing = tempDirectory.resolve("missing.jpg")
            .toFile();
        MetadataTool.writeMetadataFromJson(missing, "[{\"XMP-dc:Creator\":\"x\"}]");
        MetadataTool.wipeMetadataFromFile(missing);
        assertThat(missing).doesNotExist();
    }

    @Test
    void missingFilesYieldNoMetadataAndNoNumbers(@TempDir Path tempDirectory) throws Exception {
        var missing = tempDirectory.resolve("missing.mp4")
            .toFile();
        assertThat(MetadataTool.extractMetadataJsonObject(missing)).isNull();
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class, () -> MetadataTool.extractFrameRate(missing));
    }

    @Test
    void readsTheAudioTrackOfAVideo(@TempDir Path tempDirectory) throws Exception {
        var video = SampleFiles.mp4WithAudio(tempDirectory.resolve("talk.mp4"));

        assertThat(MetadataTool.extractAudioSampleRate(video)).isEqualTo(8000);
        assertThat(MetadataTool.extractAudioChannels(video)).isEqualTo(1);
        assertThat(MetadataTool.extractAudioCodec(video)).isNotNull();
    }

    @Test
    void anInterruptedJsonImportIsOnlyLogged(@TempDir Path tempDirectory) throws Exception {
        var image = SampleFiles.jpeg(tempDirectory.resolve("interrupted.jpg"));

        // The wipe that precedes the import waits for ExifTool on an interrupted thread; the failure is logged, not thrown
        Thread.currentThread()
            .interrupt();
        try {
            MetadataTool.writeMetadataFromJson(image, "[{\"XMP-dc:Creator\":\"Nobody\"}]");
        } finally {
            Thread.interrupted();
        }
        Thread.sleep(1000);

        assertThat(MetadataTool.extractCreatorName(MetadataTool.extractMetadataJsonObject(image))).isNull();
    }
}
