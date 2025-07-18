package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.ScanResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ArchiveFileRepository;
import fi.poltsi.vempain.file.repository.AudioFileRepository;
import fi.poltsi.vempain.file.repository.DocumentFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileRepository;
import fi.poltsi.vempain.file.repository.FontFileRepository;
import fi.poltsi.vempain.file.repository.IconFileRepository;
import fi.poltsi.vempain.file.repository.ImageFileRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.VectorFileRepository;
import fi.poltsi.vempain.file.repository.VideoFileRepository;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileScannerService {

	private final FileGroupRepository    fileGroupRepository;
	private final FileRepository         fileRepository;
	private final ImageFileRepository    imageFileRepository;
	private final VideoFileRepository    videoFileRepository;
	private final AudioFileRepository    audioFileRepository;
	private final DocumentFileRepository documentFileRepository;
	private final VectorFileRepository   vectorFileRepository;
	private final IconFileRepository     iconFileRepository;
	private final FontFileRepository     fontFileRepository;
	private final ArchiveFileRepository  archiveFileRepository;
	private final MetadataRepository     metadataRepository;

	@Transactional
	public ScanResponse scanDirectory(String rootDirectory) {
		long scannedFilesCount = 0;
		long newFilesCount     = 0;

		try {
			var leafDirectories = Files.walk(Path.of(rootDirectory))
									   .filter(Files::isDirectory)
									   .filter(this::isLeafDirectory)
									   .toList();

			for (Path leafDir : leafDirectories) {
				File[] files = leafDir.toFile()
									  .listFiles();
				if (files == null || files.length == 0) {
					log.warn("Directory is empty: {}", leafDir);
					continue;
				}

				FileGroupEntity fileGroup = fileGroupRepository.save(
						FileGroupEntity.builder()
									   .path(leafDir.toString())
									   .groupName(leafDir.getFileName()
														 .toString())
									   .build()
				);

				for (File file : files) {
					scannedFilesCount++;
					try {
						boolean saved = processFile(file, fileGroup);
						if (saved) {
							newFilesCount++;
						}
					} catch (IOException e) {
						log.error("Error processing file: {}", file.getAbsolutePath(), e);
					}
				}
			}

			return new ScanResponse(true, null, scannedFilesCount, newFilesCount);

		} catch (Exception e) {
			log.error("Directory scan failed for '{}': {}", rootDirectory, e.getMessage(), e);
			return new ScanResponse(false, e.getMessage(), scannedFilesCount, newFilesCount);
		}
	}

	private boolean isLeafDirectory(Path path) {
		try {
			return Files.list(path)
						.noneMatch(Files::isDirectory);
		} catch (IOException e) {
			log.error("Error checking if directory is leaf: {}", path, e);
			return false;
		}
	}

	private void processLeafDirectory(Path leafDirectory) {
		File[] files = leafDirectory.toFile()
									.listFiles();
		if (files == null || files.length == 0) {
			log.warn("Directory is empty: {}", leafDirectory);
			return;
		}

		FileGroupEntity fileGroup = fileGroupRepository.save(
				FileGroupEntity.builder()
							   .path(leafDirectory.toString())
							   .groupName(leafDirectory.getFileName()
													   .toString())
							   .build()
		);

		for (File file : files) {
			try {
				processFile(file, fileGroup);
			} catch (IOException e) {
				log.error("Error processing file: {}", file.getAbsolutePath(), e);
			}
		}
	}

	private boolean processFile(File file, FileGroupEntity fileGroup) throws IOException {
		String mimetype = Files.probeContentType(file.toPath());
		FileTypeEnum fileType = determineFileType(mimetype);

		if (fileType == FileTypeEnum.OTHER) {
			log.warn("Unsupported file type: {}", file.getName());
			return false;
		}

		FileEntity fileEntity = createFileEntity(fileType, file, fileGroup, mimetype);

		switch (fileType) {
			case IMAGE -> {
				ImageFileEntity imageFile = (ImageFileEntity) fileEntity;
				Dimension res = MetadataTool.extractImageResolution(file);
				imageFile.setWidth(res.width);
				imageFile.setHeight(res.height);
				imageFile.setColorDepth(MetadataTool.extractImageColorDepth(file));
				imageFile.setDpi(MetadataTool.extractImageDpi(file));
				fileRepository.save(imageFile);
			}
			case VIDEO -> {
				VideoFileEntity videoFile = (VideoFileEntity) fileEntity;
				Dimension res = MetadataTool.extractVideoResolution(file);
				videoFile.setWidth(res.width);
				videoFile.setHeight(res.height);
				videoFile.setFrameRate(MetadataTool.extractFrameRate(file));
				videoFile.setDuration(MetadataTool.extractVideoDuration(file));
				videoFile.setCodec(MetadataTool.extractVideoCodec(file));
				fileRepository.save(videoFile);
			}
			case AUDIO -> {
				AudioFileEntity audioFile = (AudioFileEntity) fileEntity;
				audioFile.setDuration(MetadataTool.extractAudioDuration(file));
				audioFile.setBitRate(MetadataTool.extractAudioBitRate(file));
				audioFile.setSampleRate(MetadataTool.extractAudioSampleRate(file));
				audioFile.setCodec(MetadataTool.extractAudioCodec(file));
				audioFile.setChannels(MetadataTool.extractAudioChannels(file));
				fileRepository.save(audioFile);
			}
			case DOCUMENT -> {
				DocumentFileEntity documentFile = (DocumentFileEntity) fileEntity;
				documentFile.setPageCount(MetadataTool.extractDocumentPageCount(file));
				documentFile.setFormat(MetadataTool.extractDocumentFormat(file));
				fileRepository.save(documentFile);
			}
			case VECTOR -> {
				VectorFileEntity vectorFile = (VectorFileEntity) fileEntity;
				Dimension res = MetadataTool.extractVectorResolution(file);
				vectorFile.setWidth(res.width);
				vectorFile.setHeight(res.height);
				vectorFile.setLayersCount(MetadataTool.extractVectorLayersCount(file));
				fileRepository.save(vectorFile);
			}
			case ICON -> {
				IconFileEntity iconFile = (IconFileEntity) fileEntity;
				Dimension res = MetadataTool.extractIconResolution(file);
				iconFile.setWidth(res.width);
				iconFile.setHeight(res.height);
				iconFile.setIsScalable(MetadataTool.extractIconIsScalable(file));
				fileRepository.save(iconFile);
			}
			case FONT -> {
				FontFileEntity fontFile = (FontFileEntity) fileEntity;
				fontFile.setFontFamily(MetadataTool.extractFontFamily(file));
				fontFile.setWeight(MetadataTool.extractFontWeight(file));
				fontFile.setStyle(MetadataTool.extractFontStyle(file));
				fileRepository.save(fontFile);
			}
			case ARCHIVE -> {
				ArchiveFileEntity archiveFile = (ArchiveFileEntity) fileEntity;
				archiveFile.setCompressionMethod(MetadataTool.extractArchiveCompressionMethod(file));
				archiveFile.setUncompressedSize(MetadataTool.extractArchiveUncompressedSize(file));
				archiveFile.setContentCount(MetadataTool.extractArchiveContentCount(file));
				archiveFile.setIsEncrypted(MetadataTool.extractArchiveIsEncrypted(file));
				fileRepository.save(archiveFile);
			}
			default -> {
				return false;
			}
		}

		processMetadata(file, fileEntity);
		return true;
	}

	private FileEntity createFileEntity(FileTypeEnum fileType, File file, FileGroupEntity fileGroup, String mimetype) {
		return switch (fileType) {
			case IMAGE -> ImageFileEntity.builder()
										 .fileGroup(fileGroup)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .sha256sum(computeSha256(file))
										 .createdAt(Instant.now())
										 .build();
			case VIDEO -> VideoFileEntity.builder()
										 .fileGroup(fileGroup)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .sha256sum(computeSha256(file))
										 .createdAt(Instant.now())
										 .build();
			case AUDIO -> AudioFileEntity.builder()
										 .fileGroup(fileGroup)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .sha256sum(computeSha256(file))
										 .createdAt(Instant.now())
										 .build();
			case DOCUMENT -> DocumentFileEntity.builder()
											   .fileGroup(fileGroup)
											   .filename(file.getName())
											   .mimetype(mimetype)
											   .filesize(file.length())
											   .sha256sum(computeSha256(file))
											   .createdAt(Instant.now())
											   .build();
			case VECTOR -> VectorFileEntity.builder()
										   .fileGroup(fileGroup)
										   .filename(file.getName())
										   .mimetype(mimetype)
										   .filesize(file.length())
										   .sha256sum(computeSha256(file))
										   .createdAt(Instant.now())
										   .build();
			case ICON -> IconFileEntity.builder()
									   .fileGroup(fileGroup)
									   .filename(file.getName())
									   .mimetype(mimetype)
									   .filesize(file.length())
									   .sha256sum(computeSha256(file))
									   .createdAt(Instant.now())
									   .build();
			case FONT -> FontFileEntity.builder()
									   .fileGroup(fileGroup)
									   .filename(file.getName())
									   .mimetype(mimetype)
									   .filesize(file.length())
									   .sha256sum(computeSha256(file))
									   .createdAt(Instant.now())
									   .build();
			case ARCHIVE -> ArchiveFileEntity.builder()
											 .fileGroup(fileGroup)
											 .filename(file.getName())
											 .mimetype(mimetype)
											 .filesize(file.length())
											 .sha256sum(computeSha256(file))
											 .createdAt(Instant.now())
											 .build();
			case OTHER -> throw new IllegalArgumentException("Unsupported file type for file: " + file.getName());
		};
	}

	private FileTypeEnum determineFileType(String mimetype) {
		if (mimetype == null) {
			return FileTypeEnum.OTHER;
		}
		if (mimetype.startsWith("image/")) {
			return FileTypeEnum.IMAGE;
		}
		if (mimetype.startsWith("video/")) {
			return FileTypeEnum.VIDEO;
		}
		if (mimetype.startsWith("audio/")) {
			return FileTypeEnum.AUDIO;
		}
		if (mimetype.startsWith("application/pdf")) {
			return FileTypeEnum.DOCUMENT;
		}
		if (mimetype.startsWith("application/postscript")) {
			return FileTypeEnum.VECTOR;
		}
		if (mimetype.startsWith("image/vnd.microsoft.icon") || mimetype.startsWith("image/x-icon")) {
			return FileTypeEnum.ICON;
		}
		if (mimetype.startsWith("font/")) {
			return FileTypeEnum.FONT;
		}
		if (mimetype.startsWith("application/zip") || mimetype.startsWith("application/x-tar")) {
			return FileTypeEnum.ARCHIVE;
		}
		return FileTypeEnum.OTHER;
	}

	private String computeSha256(File file) {
		try {
			return DigestUtils.sha256Hex(Files.readAllBytes(file.toPath()));
		} catch (IOException e) {
			return null;
		}
	}

	private void processMetadata(File file, FileEntity fileEntity) {
		// Simulate metadata extraction (e.g., using exiftool)
		MetadataEntity metadata = MetadataEntity.builder()
												.file(fileEntity)
												.metadataGroup("sample-group")
												.metadataKey("sample-key")
												.metadataValue("sample-value")
												.build();
		metadataRepository.save(metadata);
	}

	private void processImageFile(File file, FileEntity fileEntity) {
		ImageFileEntity imageFile = ImageFileEntity.builder()
												   .id(fileEntity.getId())
												   .width(1920)
												   .height(1080)
												   .colorDepth(24)
												   .dpi(300)
												   .build();
		imageFileRepository.save(imageFile);
	}

	private void processVideoFile(File file, FileEntity fileEntity) {
		VideoFileEntity videoFile = VideoFileEntity.builder()
												   .id(fileEntity.getId())
												   .width(1920)
												   .height(1080)
												   .frameRate(30.0)
												   .duration(120.5)
												   .codec("H.264")
												   .build();
		videoFileRepository.save(videoFile);
	}

	private void processAudioFile(File file, FileEntity fileEntity) {
		AudioFileEntity audioFile = AudioFileEntity.builder()
												   .id(fileEntity.getId())
												   .duration(200.5)
												   .bitRate(320)
												   .sampleRate(44100)
												   .codec("MP3")
												   .channels(2)
												   .build();
		audioFileRepository.save(audioFile);
	}

	private void processDocumentFile(File file, FileEntity fileEntity) {
		DocumentFileEntity documentFile = DocumentFileEntity.builder()
															.id(fileEntity.getId())
															.pageCount(10)
															.format("PDF")
															.build();
		documentFileRepository.save(documentFile);
	}

	private void processVectorFile(File file, FileEntity fileEntity) {
		VectorFileEntity vectorFile = VectorFileEntity.builder()
													  .id(fileEntity.getId())
													  .width(1024)
													  .height(768)
													  .layersCount(5)
													  .build();
		vectorFileRepository.save(vectorFile);
	}

	private void processIconFile(File file, FileEntity fileEntity) {
		IconFileEntity iconFile = IconFileEntity.builder()
												.id(fileEntity.getId())
												.width(256)
												.height(256)
												.isScalable(true)
												.build();
		iconFileRepository.save(iconFile);
	}

	private void processFontFile(File file, FileEntity fileEntity) {
		FontFileEntity fontFile = FontFileEntity.builder()
												.id(fileEntity.getId())
												.fontFamily("Arial")
												.weight("Bold")
												.style("Italic")
												.build();
		fontFileRepository.save(fontFile);
	}

	private void processArchiveFile(File file, FileEntity fileEntity) {
		ArchiveFileEntity archiveFile = ArchiveFileEntity.builder()
														 .id(fileEntity.getId())
														 .compressionMethod("ZIP")
														 .uncompressedSize(10485760L)
														 .contentCount(100)
														 .isEncrypted(false)
														 .build();
		archiveFileRepository.save(archiveFile);
	}
}
