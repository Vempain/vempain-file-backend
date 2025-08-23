package fi.poltsi.vempain.file.service;

import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Builder
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class VempainMultipartFile implements MultipartFile {
	private Path   path;
	private String contentType;

	@Nonnull
	@Override
	public String getName() {
		return path.getFileName()
				   .toString();
	}

	@Override
	public String getOriginalFilename() {
		return getName();
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public long getSize() {
		try {
			return Files.size(path);
		} catch (IOException e) {
			return 0;
		}
	}

	@Override
	public boolean isEmpty() {
		return getSize() == 0;
	}

	@Nonnull
	@Override
	public byte[] getBytes() throws IOException {
		return Files.readAllBytes(path);
	}

	@Nonnull
	@Override
	public InputStream getInputStream() throws IOException {
		return Files.newInputStream(path);
	}

	@Override
	public void transferTo(File dest) throws IOException, IllegalStateException {
		transferTo(dest.toPath());
	}

	@Override
	public void transferTo(@Nonnull Path dest) throws IOException, IllegalStateException {
		Files.copy(path, dest);
	}
}
