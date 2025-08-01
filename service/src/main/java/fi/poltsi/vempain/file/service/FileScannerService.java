package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.exception.VempainRuntimeException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.FileResponse;
import fi.poltsi.vempain.file.api.response.ScanResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ArchiveFileRepository;
import fi.poltsi.vempain.file.repository.AudioFileRepository;
import fi.poltsi.vempain.file.repository.DocumentFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.FontFileRepository;
import fi.poltsi.vempain.file.repository.IconFileRepository;
import fi.poltsi.vempain.file.repository.ImageFileRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.VectorFileRepository;
import fi.poltsi.vempain.file.repository.VideoFileRepository;
import fi.poltsi.vempain.file.tools.MetadataTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Objects;

import static fi.poltsi.vempain.file.tools.MetadataTool.getDescriptionFromJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalDateTimeFromJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalDocumentId;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalSecondFraction;

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
	private final AclService             aclService;
	private final TagRepository          tagRepository;
	private final FileTagRepository      fileTagRepository;

	@Value("${vempain.file-root-directory}")
	private String rootDirectory;

	private static JSONObject metadataToJsonObject(String metadata) {
		var jsonArray = new JSONArray(metadata);

		if (jsonArray.isEmpty()) {
			log.error("Failed to parse the metadata JSON from\n{}", metadata);
			return null;
		}

		return jsonArray.getJSONObject(0);
	}

	@Transactional
	public ScanResponse scanDirectory(String selectedDirectory) {
		var scannedFilesCount       = 0L;
		var newFilesCount           = 0L;
		var success                 = true;
		var errorMessage            = new StringBuilder();
		var failedFiles             = new ArrayList<String>();
		var successfulFileResponses = new ArrayList<FileResponse>();
		var leafDirectories         = new ArrayList<Path>();
		var scanDirectory           = Path.of(rootDirectory, selectedDirectory);

		try {
			leafDirectories.addAll(Files.walk(scanDirectory)
										.filter(Files::isDirectory)
										.filter(path -> !path.getFileName()
															 .toString()
															 .startsWith("."))
										.filter(this::isLeafDirectory)
										.toList());
		} catch (IOException e) {
			log.error("Error scanning directory: {}", scanDirectory, e);
			errorMessage.append("Error scanning directory: ")
						.append(scanDirectory);
			success = false;
		}

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
					var processed = processFile(file, fileGroup);

					if (processed != null && processed) {
						newFilesCount++;
						// Retrieve the saved FileEntity and convert it to FileResponse.
						var fileEntity = fileRepository.findByFilename(file.getName());

						if (fileEntity != null) {
							successfulFileResponses.add(fileEntity.toResponse());
						}
					} else if (processed != null && !processed) {
						failedFiles.add(file.getName());
						success = false;
					}
				} catch (IOException e) {
					log.error("Error processing file: {}", file.getAbsolutePath(), e);
					errorMessage.append("Error processing file: ")
								.append(file.getAbsolutePath())
								.append(" - ")
								.append(e.getMessage())
								.append("\n");
					success = false;
				}
			}
		}

		return ScanResponse.builder()
						   .success(success)
						   .scannedFilesCount(scannedFilesCount)
						   .newFilesCount(newFilesCount)
						   .failedFiles(failedFiles)
						   .successfulFiles(successfulFileResponses)
						   .errorMessage(errorMessage.toString())
						   .build();
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

	@Transactional
	protected Boolean processFile(File file, FileGroupEntity fileGroup) throws IOException {
		log.info("Processing file: {}", file.getAbsolutePath());

		var sha256sum = computeSha256(file);
		// Check first if it already exists in the database
		var existingFile = fileRepository.findByFilename(file.getName());

		if (existingFile != null) {
			// Next check if the sha256sum matches
			if (sha256sum != null
				&& sha256sum.isBlank()
				&& existingFile.getSha256sum()
							   .equals(sha256sum)) {
				log.info("Identical file already exists in the database: {}", file.getName());
				return null;
			}

			// TODO Consider updating the existing file entity
		}

		// Get the metadata in JSON format of the file
		var metadataString = MetadataTool.extractMetadataJson(file);
		// Get all metadata entries using exiftool
		log.info("Extracting metadata for file: {}", file.getAbsolutePath());

		String metadata = null;

		try {
			metadata = MetadataTool.extractMetadataJson(file);
		} catch (IOException e) {
			log.error("Failed to extract metadata for file: {}", file.getAbsolutePath(), e);
		}

		if (metadataString == null
			|| metadataString.isBlank()) {
			log.error("Failed to extract metadata from file: {}", file.getAbsolutePath());
			return false;
		}

		// Convert the metadata string to a JSON object
		var jsonObject = metadataToJsonObject(metadata);

		if (jsonObject == null) {
			log.error("Failed to convert metadata from string to JSON object: {}", metadata);
			return false;
		}

		log.debug("Extracted JSON object\n{}", jsonObject);

		var mimetype = Files.probeContentType(file.toPath());
		log.info("Probed MIME type: {}", mimetype);

		if (mimetype == null) {
			log.warn("Could not determine MIME type for file: {} by probing. Using exiftool to probe it", file.getName());
			mimetype = MetadataTool.extractMimetype(jsonObject);

			if (mimetype == null) {
				log.error("Failed to determine MIME type for file: {}", file.getName());
				return false;
			}
		}

		var fileType = determineFileType(mimetype);
		log.info("Determined file type: {}", fileType);

		if (fileType == FileTypeEnum.OTHER) {
			log.warn("Unsupported file type: {}", file.getName());
			return false;
		}

		var fileEntity = createFileEntity(fileType, file, fileGroup, mimetype, jsonObject, metadata);

		switch (fileType) {
			case IMAGE -> {
				var imageFile = (ImageFileEntity) fileEntity;
				log.info("Extracting resolution and metadata for image file: {}", file);
				var res = MetadataTool.extractImageResolution(jsonObject);

				if (res != null) {
					imageFile.setWidth(res.width);
					imageFile.setHeight(res.height);
				}

				imageFile.setColorDepth(MetadataTool.extractImageColorDepth(jsonObject));
				imageFile.setDpi(MetadataTool.extractImageDpi(jsonObject));
				fileRepository.save(imageFile);
			}
			case VIDEO -> {
				VideoFileEntity videoFile = (VideoFileEntity) fileEntity;
				Dimension       res       = MetadataTool.extractVideoResolution(file);
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
				Dimension        res        = MetadataTool.extractVectorResolution(file);
				vectorFile.setWidth(res.width);
				vectorFile.setHeight(res.height);
				vectorFile.setLayersCount(MetadataTool.extractVectorLayersCount(file));
				fileRepository.save(vectorFile);
			}
			case ICON -> {
				IconFileEntity iconFile = (IconFileEntity) fileEntity;
				Dimension      res      = MetadataTool.extractIconResolution(file);
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

		saveTags(jsonObject, fileEntity);
		processMetadata(jsonObject, fileEntity);
		return true;
	}

	@Transactional
	protected void saveTags(JSONObject jsonObject, FileEntity fileEntity) {
		var subjects = MetadataTool.getSubjects(jsonObject);

		if (subjects.isEmpty()) {
			return;
		}

		for (String subject : subjects) {
			if (subject == null || subject.isBlank()) {
				continue;
			}

			// Find or create tag
			TagEntity tag = tagRepository.findByTagName(subject)
										 .orElseGet(() -> {
											 TagEntity newTag = TagEntity.builder()
																		 .tagName(subject)
																		 .tagNameDe(null)
																		 .tagNameEn(null)
																		 .tagNameEs(null)
																		 .tagNameFi(null)
																		 .tagNameSv(null)
																		 .build();
											 return tagRepository.save(newTag);
										 });

			// Check if FileTag already exists
			boolean exists = fileTagRepository.findByFile(fileEntity)
											  .stream()
											  .anyMatch(ft -> ft.getTag()
																.getId()
																.equals(tag.getId()));
			if (!exists) {
				FileTag fileTag = FileTag.builder()
										 .file(fileEntity)
										 .tag(tag)
										 .build();
				fileTagRepository.save(fileTag);
			}
		}
	}

	private FileEntity createFileEntity(FileTypeEnum fileType, File file, FileGroupEntity fileGroup, String mimetype, JSONObject jsonObject, String metadata) {
		var userId = 0L;

		try {
			userId = AuthTools.getCurrentUserId();
		} catch (VempainAuthenticationException e) {
			log.error("Failed to get current user ID, cannot create file entity for file: {}", file.getName(), e);
			return null;
		}

		if (userId < 1) {
			log.error("Retrieved user ID is illegal: {}", file.getName());
			return null;
		}

		var sha256sum = computeSha256(file);

		// Extract comment from metadata, it may not exist
		var description = getDescriptionFromJson(jsonObject);

		var originalDateTimeString = getOriginalDateTimeFromJson(jsonObject);
		var originalDateTime       = dateTimeParser(originalDateTimeString);
		var originalSecondFraction = getOriginalSecondFraction(jsonObject);
		var originalDocumentId     = getOriginalDocumentId(jsonObject);

		// Create a new ACL entry for the file
		var aclId = 0L;
		try {
			aclId = aclService.createNewAcl(userId, null, true, true, true, true);
		} catch (VempainAclException e) {
			throw new VempainRuntimeException();
		}

		return switch (fileType) {
			case IMAGE -> ImageFileEntity.builder()
										 .aclId(aclId)
										 .fileGroup(fileGroup)
										 .externalFileId(fileType + "-" + sha256sum)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .fileType(fileType.name())
										 .sha256sum(sha256sum)
										 .creator(userId)
										 .created(Instant.now())
										 .originalDatetime(originalDateTime)
										 .originalSecondFraction(originalSecondFraction)
										 .originalDocumentId(originalDocumentId)
										 .description(description)
										 .metadataRaw(metadata)
										 .build();
			case VIDEO -> VideoFileEntity.builder()
										 .aclId(aclId)
										 .fileGroup(fileGroup)
										 .externalFileId(fileType + sha256sum)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .fileType(fileType.name())
										 .sha256sum(sha256sum)
										 .creator(userId)
										 .created(Instant.now())
										 .originalDatetime(originalDateTime)
										 .originalSecondFraction(originalSecondFraction)
										 .originalDocumentId(originalDocumentId)
										 .description(description)
										 .metadataRaw(metadata)
										 .build();
			case AUDIO -> AudioFileEntity.builder()
										 .aclId(aclId)
										 .fileGroup(fileGroup)
										 .externalFileId(fileType + sha256sum)
										 .filename(file.getName())
										 .mimetype(mimetype)
										 .filesize(file.length())
										 .fileType(fileType.name())
										 .sha256sum(sha256sum)
										 .creator(userId)
										 .created(Instant.now())
										 .originalDatetime(originalDateTime)
										 .originalSecondFraction(originalSecondFraction)
										 .originalDocumentId(originalDocumentId)
										 .description(description)
										 .metadataRaw(metadata)
										 .build();
			case DOCUMENT -> DocumentFileEntity.builder()
											   .aclId(aclId)
											   .fileGroup(fileGroup)
											   .externalFileId(fileType + sha256sum)
											   .filename(file.getName())
											   .mimetype(mimetype)
											   .filesize(file.length())
											   .fileType(fileType.name())
											   .sha256sum(sha256sum)
											   .creator(userId)
											   .created(Instant.now())
											   .originalDatetime(originalDateTime)
											   .originalSecondFraction(originalSecondFraction)
											   .originalDocumentId(originalDocumentId)
											   .description(description)
											   .metadataRaw(metadata)
											   .build();
			case VECTOR -> VectorFileEntity.builder()
										   .aclId(aclId)
										   .fileGroup(fileGroup)
										   .externalFileId(fileType + sha256sum)
										   .filename(file.getName())
										   .mimetype(mimetype)
										   .filesize(file.length())
										   .fileType(fileType.name())
										   .sha256sum(sha256sum)
										   .creator(userId)
										   .created(Instant.now())
										   .originalDatetime(originalDateTime)
										   .originalSecondFraction(originalSecondFraction)
										   .originalDocumentId(originalDocumentId)
										   .description(description)
										   .metadataRaw(metadata)
										   .build();
			case ICON -> IconFileEntity.builder()
									   .aclId(aclId)
									   .fileGroup(fileGroup)
									   .externalFileId(fileType + sha256sum)
									   .filename(file.getName())
									   .mimetype(mimetype)
									   .filesize(file.length())
									   .fileType(fileType.name())
									   .sha256sum(sha256sum)
									   .creator(userId)
									   .created(Instant.now())
									   .originalDatetime(originalDateTime)
									   .originalSecondFraction(originalSecondFraction)
									   .originalDocumentId(originalDocumentId)
									   .description(description)
									   .metadataRaw(metadata)
									   .build();
			case FONT -> FontFileEntity.builder()
									   .aclId(aclId)
									   .fileGroup(fileGroup)
									   .externalFileId(fileType + sha256sum)
									   .filename(file.getName())
									   .mimetype(mimetype)
									   .filesize(file.length())
									   .fileType(fileType.name())
									   .sha256sum(sha256sum)
									   .creator(userId)
									   .created(Instant.now())
									   .originalDatetime(originalDateTime)
									   .originalSecondFraction(originalSecondFraction)
									   .originalDocumentId(originalDocumentId)
									   .description(description)
									   .metadataRaw(metadata)
									   .build();
			case ARCHIVE -> ArchiveFileEntity.builder()
											 .aclId(aclId)
											 .fileGroup(fileGroup)
											 .externalFileId(fileType + sha256sum)
											 .filename(file.getName())
											 .mimetype(mimetype)
											 .filesize(file.length())
											 .fileType(fileType.name())
											 .sha256sum(sha256sum)
											 .creator(userId)
											 .created(Instant.now())
											 .originalDatetime(originalDateTime)
											 .originalSecondFraction(originalSecondFraction)
											 .originalDocumentId(originalDocumentId)
											 .description(description)
											 .metadataRaw(metadata)
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

	@Transactional
	protected void processMetadata(JSONObject jsonObject, FileEntity fileEntity) {
		var metadataEntities = new ArrayList<MetadataEntity>();

		for (String group : jsonObject.keySet()) {
			var groupValue = jsonObject.get(group);

			if (!(groupValue instanceof JSONObject groupObject)) {
				// Skip non-JSONObject entries (e.g., "SourceFile": "/path/to/file")
				continue;
			}
			for (var key : groupObject.keySet()) {
				var value    = groupObject.get(key);
				var valueStr = Objects.toString(value, null);
				var entity = MetadataEntity.builder()
										   .file(fileEntity)
										   .metadataGroup(group)
										   .metadataKey(key)
										   .metadataValue(valueStr)
										   .build();
				metadataEntities.add(entity);
			}
		}
		metadataRepository.saveAll(metadataEntities);
	}

	private Instant dateTimeParser(String dateTimeString) {
		if (dateTimeString == null || dateTimeString.isBlank()) {
			return null;
		}

		DateTimeFormatter formatter = new DateTimeFormatterBuilder()
				// Try: yyyy:MM:dd HH:mm:ss.SSS[S...] (allows up to 9 digits)
				.appendPattern("yyyy:MM:dd HH:mm:ss")
				.optionalStart()
				.appendLiteral('.')
				.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, false)
				.optionalEnd()
				.toFormatter()
				.withZone(ZoneId.systemDefault());

		return Instant.from(formatter.parse(dateTimeString, Instant::from));
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
