package fi.poltsi.vempain.file.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

class ImageToolUTC {

	@Test
	void resizeImageKeepsSmallImageDimensions(@TempDir Path tempDirectory) throws Exception {
		var source      = createImage(tempDirectory.resolve("small.png"), 80, 40);
		var destination = tempDirectory.resolve("small.jpg");
		var tool        = new ImageTool();

		try (MockedStatic<MetadataTool> metadata = mockStatic(MetadataTool.class)) {
			assertThat(tool.resizeImage(source, destination, 100, 0.7f, null))
					.isEqualTo(new Dimension(80, 40));
			metadata.verify(() -> MetadataTool.copyMetadata(source.toFile(), destination.toFile()));
		}
		assertThat(ImageIO.read(destination.toFile())).isNotNull();
	}

	@Test
	void resizeImageScalesLandscapeAndPortraitToMinimumSize(@TempDir Path tempDirectory) throws Exception {
		var landscape = createImage(tempDirectory.resolve("landscape.png"), 200, 100);
		var portrait  = createImage(tempDirectory.resolve("portrait.png"), 100, 200);
		var tool      = new ImageTool();

		try (MockedStatic<MetadataTool> metadata = mockStatic(MetadataTool.class)) {
			assertThat(tool.resizeImage(landscape, tempDirectory.resolve("landscape.jpg"), 100, 0.7f, "{}"))
					.isEqualTo(new Dimension(200, 100));
			assertThat(tool.resizeImage(portrait, tempDirectory.resolve("portrait.jpg"), 100, 0.7f, "{}"))
					.isEqualTo(new Dimension(100, 200));
			metadata.verify(() -> MetadataTool.writeMetadataFromJson(
					landscape.toFile(), "{}"), org.mockito.Mockito.never());
		}
	}

	@Test
	void missingImageFailsWithInternalServerError(@TempDir Path tempDirectory) {
		var exception = assertThrows(ResponseStatusException.class,
									 () -> new ImageTool().getImageDimensions(tempDirectory.resolve("missing.jpg")));

		assertThat(exception.getStatusCode()
		                    .value()).isEqualTo(500);
	}

	@Test
	void unreadableImageFailsWithInternalServerError(@TempDir Path tempDirectory) throws Exception {
		var source = tempDirectory.resolve("not-an-image.bin");
		Files.writeString(source, "not an image");

		var exception = assertThrows(ResponseStatusException.class,
									 () -> new ImageTool().getImageDimensions(source));

		assertThat(exception.getStatusCode()
		                    .value()).isEqualTo(500);
	}

	private Path createImage(Path path, int width, int height) throws Exception {
		var image    = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ImageIO.write(image, "png", path.toFile());
		return path;
	}
}
