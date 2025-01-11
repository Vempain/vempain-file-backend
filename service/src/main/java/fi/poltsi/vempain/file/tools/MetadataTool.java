package fi.poltsi.vempain.file.tools;


import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class MetadataTool {

	public static Dimension extractImageResolution(File file) throws IOException {
		String output = runExifTool(file, "-ImageWidth", "-ImageHeight");
		int width = Integer.parseInt(getTagValue(output, "ImageWidth"));
		int height = Integer.parseInt(getTagValue(output, "ImageHeight"));
		return new Dimension(width, height);
	}

	public static int extractImageColorDepth(File file) throws IOException {
		String output = runExifTool(file, "-BitsPerSample");
		return Integer.parseInt(getTagValue(output, "BitsPerSample"));
	}

	public static int extractImageDpi(File file) throws IOException {
		String output = runExifTool(file, "-XResolution");
		return Integer.parseInt(getTagValue(output, "XResolution"));
	}

	public static Dimension extractVideoResolution(File file) throws IOException {
		String output = runExifTool(file, "-ImageWidth", "-ImageHeight");
		int width = Integer.parseInt(getTagValue(output, "ImageWidth"));
		int height = Integer.parseInt(getTagValue(output, "ImageHeight"));
		return new Dimension(width, height);
	}

	public static double extractFrameRate(File file) throws IOException {
		String output = runExifTool(file, "-VideoFrameRate");
		return Double.parseDouble(getTagValue(output, "VideoFrameRate"));
	}

	public static double extractVideoDuration(File file) throws IOException {
		String output = runExifTool(file, "-Duration");
		return Double.parseDouble(getTagValue(output, "Duration"));
	}

	public static String extractVideoCodec(File file) throws IOException {
		String output = runExifTool(file, "-VideoCodec");
		return getTagValue(output, "VideoCodec");
	}

	public static double extractAudioDuration(File file) throws IOException {
		String output = runExifTool(file, "-Duration");
		return Double.parseDouble(getTagValue(output, "Duration"));
	}

	public static int extractAudioBitRate(File file) throws IOException {
		String output = runExifTool(file, "-AudioBitrate");
		return Integer.parseInt(getTagValue(output, "AudioBitrate"));
	}

	public static int extractAudioSampleRate(File file) throws IOException {
		String output = runExifTool(file, "-AudioSampleRate");
		return Integer.parseInt(getTagValue(output, "AudioSampleRate"));
	}

	public static String extractAudioCodec(File file) throws IOException {
		String output = runExifTool(file, "-AudioCodec");
		return getTagValue(output, "AudioCodec");
	}

	public static int extractAudioChannels(File file) throws IOException {
		String output = runExifTool(file, "-AudioChannels");
		return Integer.parseInt(getTagValue(output, "AudioChannels"));
	}

	public static int extractDocumentPageCount(File file) throws IOException {
		String output = runExifTool(file, "-PageCount");
		return Integer.parseInt(getTagValue(output, "PageCount"));
	}

	public static String extractDocumentFormat(File file) throws IOException {
		String output = runExifTool(file, "-FileType");
		return getTagValue(output, "FileType");
	}

	public static Dimension extractVectorResolution(File file) throws IOException {
		String output = runExifTool(file, "-ImageWidth", "-ImageHeight");
		int width = Integer.parseInt(getTagValue(output, "ImageWidth"));
		int height = Integer.parseInt(getTagValue(output, "ImageHeight"));
		return new Dimension(width, height);
	}

	public static int extractVectorLayersCount(File file) throws IOException {
		String output = runExifTool(file, "-Layers");
		return Integer.parseInt(getTagValue(output, "Layers"));
	}

	public static Dimension extractIconResolution(File file) throws IOException {
		String output = runExifTool(file, "-ImageWidth", "-ImageHeight");
		int width = Integer.parseInt(getTagValue(output, "ImageWidth"));
		int height = Integer.parseInt(getTagValue(output, "ImageHeight"));
		return new Dimension(width, height);
	}

	public static boolean extractIconIsScalable(File file) throws IOException {
		String output = runExifTool(file, "-Scalable");
		return Boolean.parseBoolean(getTagValue(output, "Scalable"));
	}

	public static String extractFontFamily(File file) throws IOException {
		String output = runExifTool(file, "-FontFamily");
		return getTagValue(output, "FontFamily");
	}

	public static String extractFontWeight(File file) throws IOException {
		String output = runExifTool(file, "-FontWeight");
		return getTagValue(output, "FontWeight");
	}

	public static String extractFontStyle(File file) throws IOException {
		String output = runExifTool(file, "-FontStyle");
		return getTagValue(output, "FontStyle");
	}

	public static String extractArchiveCompressionMethod(File file) throws IOException {
		String output = runExifTool(file, "-Compression");
		return getTagValue(output, "Compression");
	}

	public static long extractArchiveUncompressedSize(File file) throws IOException {
		String output = runExifTool(file, "-UncompressedSize");
		return Long.parseLong(getTagValue(output, "UncompressedSize"));
	}

	public static int extractArchiveContentCount(File file) throws IOException {
		String output = runExifTool(file, "-ContentCount");
		return Integer.parseInt(getTagValue(output, "ContentCount"));
	}

	public static boolean extractArchiveIsEncrypted(File file) throws IOException {
		String output = runExifTool(file, "-Encrypted");
		return Boolean.parseBoolean(getTagValue(output, "Encrypted"));
	}

	private static String runExifTool(File file, String... tags) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();
		processBuilder.command("exiftool", String.join(" ", tags));
		processBuilder.redirectInput(file);
		Process process = processBuilder.start();
		Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\\A");
		return scanner.hasNext() ? scanner.next() : "";
	}

	private static String getTagValue(String output, String tag) {
		for (String line : output.split("\n")) {
			if (line.contains(tag)) {
				return line.split(":")[1].trim();
			}
		}
		return "";
	}
}
