package fi.poltsi.vempain.file.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageTool {

	private static final String RESPONSE_STATUS_EXCEPTION_MESSAGE = "Unknown error";

	public Dimension resizeImage(Path sourceFile, Path destinationFile, int imageMinimumSize, float quality) {
		// Get the original dimensions of the source file in order to see whether it should be resized
		var origDimensions   = getImageDimensions(sourceFile);
		var targetDimensions = new Dimension();
		var imageFormat = destinationFile.toString()
										 .substring(destinationFile.toString()
																   .lastIndexOf(".") + 1);

		// If the original image is smaller than the minimum size, just copy it
		if (origDimensions.height < imageMinimumSize || origDimensions.width < imageMinimumSize) {
			targetDimensions.setSize(origDimensions.width, origDimensions.height);
		} else {
			// Set the target dimensions so that the smaller dimension is equal to imageMinimumSize
			if (origDimensions.height > origDimensions.width) {
				targetDimensions.setSize(imageMinimumSize, (int) (origDimensions.height * ((double) imageMinimumSize / origDimensions.width)));
			} else {
				targetDimensions.setSize((int) (origDimensions.width * ((double) imageMinimumSize / origDimensions.height)), imageMinimumSize);
			}
		}

		try {
			Thumbnails
					.of(sourceFile.toFile())
					.size(targetDimensions.width, targetDimensions.height)
					.outputFormat(imageFormat)
					.outputQuality(quality)
					.allowOverwrite(true)
					.useExifOrientation(true)
					.toFile(destinationFile.toFile());

			MetadataTool.copyMetadata(sourceFile.toFile(), destinationFile.toFile());
		} catch (IOException e) {
			log.error("Failed to copy/convert {} to {}", sourceFile, destinationFile, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, RESPONSE_STATUS_EXCEPTION_MESSAGE);
		}

		return targetDimensions;
	}

	public Dimension getImageDimensions(Path imageFile) {
		if (!imageFile.toFile()
					  .exists()) {
			log.error("File does not exist: {}", imageFile);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, RESPONSE_STATUS_EXCEPTION_MESSAGE);
		}

		try (var in = ImageIO.createImageInputStream(imageFile.toFile())) {
			if (in == null) {
				log.error("Failed to create ImageInputStream from {}", imageFile);
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, RESPONSE_STATUS_EXCEPTION_MESSAGE);
			}

			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);

			if (readers.hasNext()) {
				ImageReader reader = readers.next();

				try {
					reader.setInput(in);
					return new Dimension(reader.getWidth(0), reader.getHeight(0));
				} finally {
					reader.dispose();
				}
			}
		} catch (IOException e) {
			log.error("Failed to read metadata from thumb file: {}", imageFile);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, RESPONSE_STATUS_EXCEPTION_MESSAGE);
		}

		log.error("Unknown error to read metadata from thumb file: {}", imageFile);
		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, RESPONSE_STATUS_EXCEPTION_MESSAGE);
	}
}
