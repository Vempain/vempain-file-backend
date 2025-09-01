package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.exception.VempainRuntimeException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.api.response.FileResponse;
import fi.poltsi.vempain.file.api.response.ScanExportResponse;
import fi.poltsi.vempain.file.api.response.ScanOriginalResponse;
import fi.poltsi.vempain.file.api.response.ScanResponses;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
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
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Objects;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMetadataJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.getDescriptionFromJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalDateTimeFromJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalDocumentId;
import static fi.poltsi.vempain.file.tools.MetadataTool.getOriginalSecondFraction;
import static fi.poltsi.vempain.file.tools.MetadataTool.metadataToJsonObject;

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
	private final TagRepository          tagRepository;
	private final FileTagRepository      fileTagRepository;

	private final AclService              aclService;
	private final DerivativeLookupService derivativeLookupService;
	private final ExportedFilesService    exportedFilesService;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Transactional
	public ScanResponses scanDirectories(ScanRequest scanRequest) {
		var scanResponses = new ScanResponses();

		if (scanRequest.getOriginalDirectory() != null) {
			var originalResult = scanOriginalDirectory(scanRequest.getOriginalDirectory());
			log.info("Result of scanning original directory {}: {}", scanRequest.getOriginalDirectory(), originalResult);
			scanResponses.setScanOriginalResponse(originalResult);
		}

		if (scanRequest.getExportDirectory() != null) {
			var exportedResult = scanExportDirectory(scanRequest.getExportDirectory());
			log.info("Result of scanning exported directory {}: {}", scanRequest.getExportDirectory(), exportedResult);
			scanResponses.setScanExportResponse(exportedResult);
		}

		return scanResponses;
	}

	@Transactional
	protected ScanOriginalResponse scanOriginalDirectory(String selectedDirectory) {
		var scannedFilesCount       = 0L;
		var newFilesCount           = 0L;
		var success                 = true;
		var errorMessage            = new StringBuilder();
		var failedFiles             = new ArrayList<String>();
		var successfulFileResponses = new ArrayList<FileResponse>();
		var leafDirectories         = new ArrayList<Path>();
		var scanDirectory           = Path.of(originalRootDirectory, selectedDirectory);

		success = populateLeafDirectory(leafDirectories, errorMessage, scanDirectory);

		for (var leafDir : leafDirectories) {
			var files = leafDir.toFile()
							   .listFiles();

			if (files == null || files.length == 0) {
				log.warn("Directory is empty: {}", leafDir);
				continue;
			}

			var relativeDirectory = computeRelativeFilePath(originalRootDirectory, leafDir.toFile());
			var fileGroup = fileGroupRepository.save(
					FileGroupEntity.builder()
								   .path(relativeDirectory)
								   .groupName(leafDir.getFileName()
													 .toString())
								   .build()
			);

			for (var file : files) {
				scannedFilesCount++;

				try {
					var processed = processFile(file, fileGroup);

					if (processed != null && processed) {
						newFilesCount++;
						// Retrieve the saved FileEntity and convert it to FileResponse.
						var fileEntity = fileRepository.findByFilename(file.getName());

						if (fileEntity != null) {
							// First we reset the metadataRaw field to null so that it slims down the response size.
							var fileResponse = fileEntity.toResponse();
							fileResponse.setMetadataRaw(null);
							successfulFileResponses.add(fileResponse);
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

		return ScanOriginalResponse.builder()
								   .success(success)
								   .scannedFilesCount(scannedFilesCount)
								   .newFilesCount(newFilesCount)
								   .failedFiles(failedFiles)
								   .successfulFiles(successfulFileResponses)
								   .errorMessage(errorMessage.toString())
								   .build();
	}

	@Transactional
	protected ScanExportResponse scanExportDirectory(String exportedDirectory) {
		var scannedFilesCount       = 0L;
		var newFilesCount           = 0L;
		var success                 = true;
		var errorMessage            = new StringBuilder();
		var orphanedFiles           = new ArrayList<String>();
		var successfulFileResponses = new ArrayList<ExportFileResponse>();
		var leafDirectories         = new ArrayList<Path>();
		var scanDirectory           = Path.of(exportRootDirectory, exportedDirectory);

		success = populateLeafDirectory(leafDirectories, errorMessage, scanDirectory);

		if (!success) {
			return ScanExportResponse.builder()
									 .success(false)
									 .errorMessage(errorMessage.toString())
									 .build();
		}

		for (Path leafDir : leafDirectories) {
			var files = leafDir.toFile()
							   .listFiles();

			if (files == null || files.length == 0) {
				log.warn("Derivative directory is empty: {}", leafDir);
				continue;
			}

			for (var file : files) {
				scannedFilesCount++;

				JSONObject metadataObject;

				try {
					metadataObject = MetadataTool.extractMetadataJsonObject(file);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				var originalDocumentId = MetadataTool.getOriginalDocumentId(metadataObject);

				if (exportedFilesService.existsByOriginalDocumentId(originalDocumentId)
					|| exportedFilesService.existsByPathAndFilename(file.getPath(), file.getName())) {
					log.info("Found already registered exported file at {}: {}", originalDocumentId, file.getName());
					continue;
				}

				newFilesCount++;

				// Search for the original file using the document ID
				var originalFileEntity = fileRepository.findByOriginalDocumentId(originalDocumentId);

				if (originalFileEntity == null) {
					log.warn("No original file found for exported file: {}", file.getName());
					orphanedFiles.add(file.getName());
					success = false;
					continue; // Skip processing this file
				}

				// Finally save the exported file entity
				// First get the sha256sum of the exported file
				var sha256sum        = computeSha256(file);
				var mimetype         = MetadataTool.extractMimetype(metadataObject);
				var relativeFilePath = computeRelativeFilePath(exportRootDirectory, file);

				var exportFileEntity = ExportFileEntity.builder()
													   .file(originalFileEntity)
													   .filename(file.getName())
													   .filePath(relativeFilePath)
													   .originalDocumentId(originalDocumentId)
													   .mimetype(mimetype)
													   .filesize(file.length())
													   .sha256sum(sha256sum)
													   .created(Instant.now())
													   .build();

// Save the exported file entity onto the database.
				var storedExportFile = exportedFilesService.save(exportFileEntity);
				log.info("Successfully registered exported file: {}", storedExportFile.getFilename());
				successfulFileResponses.add(storedExportFile.toResponse());

				// If there is no existing file entity, we may not create a new one
				// So the order is: The FileEntity must always exist in the database before we can process the exported file
			}
		}

		return ScanExportResponse.builder()
								 .success(success)
								 .scannedFilesCount(scannedFilesCount)
								 .newFilesCount(newFilesCount)
								 .failedFiles(orphanedFiles)
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

		// Get all metadata entries using exiftool
		log.info("Extracting metadata for file: {}", file.getAbsolutePath());

		String metadata = null;

		try {
			metadata = extractMetadataJson(file);
		} catch (IOException e) {
			log.error("Failed to extract metadata for file: {}", file.getAbsolutePath(), e);
		}

		if (metadata == null
			|| metadata.isBlank()) {
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

		// Compute relative file path
		String relativeFilePath = computeRelativeFilePath(originalRootDirectory, file);

		var fileEntity = createFileEntity(fileType, file, fileGroup, mimetype, jsonObject, metadata, relativeFilePath);

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
				var videoFile = (VideoFileEntity) fileEntity;
				var res = MetadataTool.extractXYResolution(file);
				videoFile.setWidth(res.width);
				videoFile.setHeight(res.height);
				videoFile.setFrameRate(MetadataTool.extractFrameRate(file));
				videoFile.setDuration(MetadataTool.extractAudioVideoDuration(file));
				videoFile.setCodec(MetadataTool.extractVideoCodec(file));
				fileRepository.save(videoFile);
			}
			case AUDIO -> {
				var audioFile = (AudioFileEntity) fileEntity;
				audioFile.setDuration(MetadataTool.extractAudioVideoDuration(file));
				audioFile.setBitRate(MetadataTool.extractAudioBitRate(file));
				audioFile.setSampleRate(MetadataTool.extractAudioSampleRate(file));
				audioFile.setCodec(MetadataTool.extractAudioCodec(file));
				audioFile.setChannels(MetadataTool.extractAudioChannels(file));
				fileRepository.save(audioFile);
			}
			case DOCUMENT -> {
				var documentFile = (DocumentFileEntity) fileEntity;
				documentFile.setPageCount(MetadataTool.extractDocumentPageCount(file));
				documentFile.setFormat(MetadataTool.extractDocumentFormat(file));
				fileRepository.save(documentFile);
			}
			case VECTOR -> {
				var vectorFile = (VectorFileEntity) fileEntity;
				var res = MetadataTool.extractXYResolution(file);
				vectorFile.setWidth(res.width);
				vectorFile.setHeight(res.height);
				vectorFile.setLayersCount(MetadataTool.extractVectorLayersCount(file));
				fileRepository.save(vectorFile);
			}
			case ICON -> {
				var iconFile = (IconFileEntity) fileEntity;
				var res = MetadataTool.extractXYResolution(file);
				iconFile.setWidth(res.width);
				iconFile.setHeight(res.height);
				iconFile.setIsScalable(MetadataTool.extractIconIsScalable(file));
				fileRepository.save(iconFile);
			}
			case FONT -> {
				var fontFile = (FontFileEntity) fileEntity;
				fontFile.setFontFamily(MetadataTool.extractFontFamily(file));
				fontFile.setWeight(MetadataTool.extractFontWeight(file));
				fontFile.setStyle(MetadataTool.extractFontStyle(file));
				fileRepository.save(fontFile);
			}
			case ARCHIVE -> {
				var archiveFile = (ArchiveFileEntity) fileEntity;
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

		for (var subject : subjects) {
			if (subject == null || subject.isBlank()) {
				continue;
			}

			// Find or create tag
			var tag = tagRepository.findByTagName(subject)
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
			var exists = fileTagRepository.findByFile(fileEntity)
										  .stream()
										  .anyMatch(ft -> ft.getTag()
															.getId()
															.equals(tag.getId()));
			if (!exists) {
				var fileTag = FileTag.builder()
									 .file(fileEntity)
									 .tag(tag)
									 .build();
				fileTagRepository.save(fileTag);
			}
		}
	}

	// Helper to compute relative file path
	private String computeRelativeFilePath(String rootDir, File file) {
		var rootPath = Path.of(rootDir)
						   .toAbsolutePath()
						   .normalize();
		var filePath = file.toPath()
						   .toAbsolutePath()
						   .normalize();
		// Remove the filename from the file path
		if (filePath.getFileName() != null) {
			filePath = filePath.getParent();
		}
		var relPath = rootPath.relativize(filePath);
		return "/" + relPath.toString()
							.replace(File.separatorChar, '/');
	}

	// Add relativeFilePath parameter
	private FileEntity createFileEntity(FileTypeEnum fileType, File file, FileGroupEntity fileGroup, String mimetype, JSONObject jsonObject, String metadata, String relativeFilePath) {
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
										 .filePath(relativeFilePath)
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
										 .filePath(relativeFilePath)
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
										 .filePath(relativeFilePath)
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
											   .filePath(relativeFilePath)
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
										   .filePath(relativeFilePath)
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
									   .filePath(relativeFilePath)
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
									   .filePath(relativeFilePath)
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
											 .filePath(relativeFilePath)
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

	protected Instant dateTimeParser(String dateTimeString) {
		if (dateTimeString == null || dateTimeString.isBlank()) {
			return null;
		}

		var formatter = new DateTimeFormatterBuilder()
				// Date
				.appendPattern("yyyy:MM:dd HH:mm:ss")
				// Optional fractional seconds (from 1 to 9 digits)
				.optionalStart()
				.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
				.optionalEnd()
				// Optional offset like +02:00
				.optionalStart()
				.appendOffset("+HH:mm", "Z")
				.optionalEnd()
				.toFormatter()
				.withZone(ZoneId.systemDefault());

		try {
			var timeStamp = formatter.parse(dateTimeString, Instant::from);
			log.info("Parsed date time string '{}' to Instant: {}", dateTimeString, timeStamp);
			return timeStamp;
		} catch (DateTimeParseException e) {
			log.error("Failed to parse date time string: {}", dateTimeString, e);
			return null;
		}
	}

	private void processImageFile(File file, FileEntity fileEntity) {
		var imageFile = ImageFileEntity.builder()
									   .id(fileEntity.getId())
									   .width(1920)
									   .height(1080)
									   .colorDepth(24)
									   .dpi(300)
									   .build();
		imageFileRepository.save(imageFile);
	}

	private void processVideoFile(File file, FileEntity fileEntity) {
		var videoFile = VideoFileEntity.builder()
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
		var audioFile = AudioFileEntity.builder()
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
		var documentFile = DocumentFileEntity.builder()
											 .id(fileEntity.getId())
											 .pageCount(10)
											 .format("PDF")
											 .build();
		documentFileRepository.save(documentFile);
	}

	private void processVectorFile(File file, FileEntity fileEntity) {
		var vectorFile = VectorFileEntity.builder()
										 .id(fileEntity.getId())
										 .width(1024)
										 .height(768)
										 .layersCount(5)
										 .build();
		vectorFileRepository.save(vectorFile);
	}

	private void processIconFile(File file, FileEntity fileEntity) {
		var iconFile = IconFileEntity.builder()
									 .id(fileEntity.getId())
									 .width(256)
									 .height(256)
									 .isScalable(true)
									 .build();
		iconFileRepository.save(iconFile);
	}

	private void processFontFile(File file, FileEntity fileEntity) {
		var fontFile = FontFileEntity.builder()
									 .id(fileEntity.getId())
									 .fontFamily("Arial")
									 .weight("Bold")
									 .style("Italic")
									 .build();
		fontFileRepository.save(fontFile);
	}

	private void processArchiveFile(File file, FileEntity fileEntity) {
		var archiveFile = ArchiveFileEntity.builder()
										   .id(fileEntity.getId())
										   .compressionMethod("ZIP")
										   .uncompressedSize(10485760L)
										   .contentCount(100)
										   .isEncrypted(false)
										   .build();
		archiveFileRepository.save(archiveFile);
	}

	private boolean populateLeafDirectory(ArrayList<Path> leafDirectories, StringBuilder errorMessage, Path scanDirectory) {
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
			return false;
		}

		return true;
	}
}
