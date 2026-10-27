package fi.poltsi.vempain.file.tools;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates small real files of the types the scanner and {@link FileMetadataTool} handle, so that integration tests can run ExifTool and FFmpeg on genuine
 * content without checking binary fixtures in.
 */
public final class SampleFiles {

    private SampleFiles() {}

    /**
     * A 16x16 blue JPEG written by ImageIO.
     */
    public static File jpeg(Path target) throws IOException {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        ImageIO.write(image, "jpg", target.toFile());
        return target.toFile();
    }

    /**
     * Runs {@code exiftool -overwrite_original <tags> file} and fails the test when ExifTool reports an error.
     */
    public static void exiftool(File file, String... tags) throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of("exiftool", "-overwrite_original"));
        command.addAll(List.of(tags));
        command.add(file.getAbsolutePath());
        var process = new ProcessBuilder(command).redirectErrorStream(true)
            .start();
        var output = new String(process.getInputStream()
            .readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output)
            .isZero();
    }

    /**
     * Two seconds of 16 bit mono silence at 8 kHz (whole seconds, because the audio duration column stores seconds).
     */
    public static File wav(Path target) throws IOException {
        var format = new AudioFormat(8000f, 16, 1, true, false);
        var data = new byte[8000 * 2 * 2];
        try (var stream = new AudioInputStream(new ByteArrayInputStream(data), format, data.length / 2)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, target.toFile());
        }
        return target.toFile();
    }

    /**
     * A one second 64x48 MPEG-4 clip at 12 frames per second, without audio.
     */
    public static File mp4(Path target) throws Exception {
        try (var recorder = new FFmpegFrameRecorder(target.toString(), 64, 48); var converter = new Java2DFrameConverter()) {
            recorder.setFormat("mp4");
            recorder.setFrameRate(12);
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
            recorder.start();
            var image = new BufferedImage(64, 48, BufferedImage.TYPE_3BYTE_BGR);
            var graphics = image.createGraphics();
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, 64, 48);
            graphics.dispose();
            for (int i = 0; i < 12; i++) {
                recorder.record(converter.convert(image));
            }
            recorder.stop();
        }
        return target.toFile();
    }

    /**
     * A one page PDF with a valid cross reference table, which ExifTool needs to report the page count.
     */
    public static File pdf(Path target) throws IOException {
        var objects = List.of("<</Type/Catalog/Pages 2 0 R>>", "<</Type/Pages/Kids[3 0 R]/Count 1>>", "<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 100]>>");
        var out = new StringBuilder("%PDF-1.4\n");
        var offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.length());
            out.append(i + 1)
                .append(" 0 obj\n")
                .append(objects.get(i))
                .append("\nendobj\n");
        }
        var xref = out.length();
        out.append("xref\n0 ")
            .append(objects.size() + 1)
            .append("\n0000000000 65535 f \n");
        for (var offset : offsets) {
            out.append(String.format("%010d 00000 n \n", offset));
        }
        out.append("trailer\n<</Size ")
            .append(objects.size() + 1)
            .append("/Root 1 0 R>>\nstartxref\n")
            .append(xref)
            .append("\n%%EOF\n");
        Files.write(target, out.toString()
            .getBytes(StandardCharsets.US_ASCII));
        return target.toFile();
    }

    /**
     * A 16x16 32 bit icon: ICONDIR, one ICONDIRENTRY and an uncompressed BMP image with its AND mask.
     */
    public static File ico(Path target) throws IOException {
        int width = 16;
        int height = 16;
        var pixels = width * height * 4;
        var mask = width * 4;
        var image = ByteBuffer.allocate(40 + pixels + mask)
            .order(ByteOrder.LITTLE_ENDIAN);
        image.putInt(40)
            .putInt(width)
            .putInt(height * 2)
            .putShort((short) 1)
            .putShort((short) 32)
            .putInt(0)
            .putInt(pixels + mask)
            .putInt(0)
            .putInt(0)
            .putInt(0)
            .putInt(0);
        for (int i = 0; i < width * height; i++) {
            image.put((byte) 0)
                .put((byte) 0)
                .put((byte) 0xFF)
                .put((byte) 0xFF);
        }
        image.put(new byte[mask]);
        var ico = ByteBuffer.allocate(6 + 16 + image.capacity())
            .order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0)
            .putShort((short) 1)
            .putShort((short) 1);
        ico.put((byte) width)
            .put((byte) height)
            .put((byte) 0)
            .put((byte) 0)
            .putShort((short) 1)
            .putShort((short) 32)
            .putInt(image.capacity())
            .putInt(22);
        ico.put(image.array());
        Files.write(target, ico.array());
        return target.toFile();
    }

    /**
     * A 64x32 SVG with one rectangle.
     */
    public static File svg(Path target) throws IOException {
        Files.writeString(target, "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"64\" height=\"32\">"
                + "<rect width=\"64\" height=\"32\" fill=\"red\"/></svg>");
        return target.toFile();
    }

    /**
     * A gzip archive of a short text.
     */
    public static File gzip(Path target) throws IOException {
        try (var out = new GZIPOutputStream(Files.newOutputStream(target))) {
            out.write("hello world ".repeat(50)
                .getBytes(StandardCharsets.UTF_8));
        }
        return target.toFile();
    }

    /**
     * The first TrueType font installed on the host, if any.
     */
    public static Optional<Path> systemFont() {
        var preferred = Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        if (Files.exists(preferred)) {
            return Optional.of(preferred);
        }
        var root = Path.of("/usr/share/fonts");
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (var fonts = Files.walk(root)) {
            return fonts.filter(path -> path.toString()
                .endsWith(".ttf"))
                .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * A half second 16x12 MPEG-4 clip with one AAC audio channel at 8 kHz.
     */
    public static File mp4WithAudio(Path target) throws Exception {
        try (var recorder = new FFmpegFrameRecorder(target.toString(), 16, 12, 1); var converter = new Java2DFrameConverter()) {
            recorder.setFormat("mp4");
            recorder.setFrameRate(10);
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4);
            recorder.setAudioCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC);
            recorder.setSampleRate(8000);
            recorder.setAudioChannels(1);
            recorder.start();
            var image = new BufferedImage(16, 12, BufferedImage.TYPE_3BYTE_BGR);
            var graphics = image.createGraphics();
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 16, 12);
            graphics.dispose();
            for (int i = 0; i < 5; i++) {
                recorder.record(converter.convert(image));
                recorder.recordSamples(8000, 1, ShortBuffer.wrap(new short[800]));
            }
            recorder.stop();
        }
        return target.toFile();
    }
}
