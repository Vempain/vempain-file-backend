package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.exception.VempainRuntimeException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.api.response.FileResponse;
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
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.dateTimeParser;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractArchiveCompressionMethod;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractArchiveContentCount;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractArchiveIsEncrypted;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractArchiveUncompressedSize;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractAudioBitRate;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractAudioChannels;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractAudioCodec;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractAudioSampleRate;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractAudioVideoDuration;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractCreatorCountry;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractCreatorEmail;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractCreatorName;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractCreatorUrl;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractDescription;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractDocumentFormat;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractDocumentPageCount;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractFontFamily;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractFontStyle;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractFontWeight;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractFrameRate;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractGpsData;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractGpsTime;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractIconIsScalable;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractImageColorDepth;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractImageDpi;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractImageResolution;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractLabel;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMetadataJson;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMetadataJsonObject;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMimetype;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractOriginalDateTime;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractOriginalDocumentId;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractOriginalSecondFraction;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractRightsHolder;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractRightsTerms;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractRightsUrl;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractSubjects;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractVectorLayersCount;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractVideoCodec;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractXYResolution;
import static fi.poltsi.vempain.file.tools.MetadataTool.metadataToJsonObject;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryProcessorService {

	private final FileGroupRepository  fileGroupRepository;
	private final FileRepository       fileRepository;
	private final TagRepository        tagRepository;
	private final MetadataRepository   metadataRepository;
	private final FileTagRepository    fileTagRepository;
	private final ExportFileRepository exportFileRepository;

	private final AclService            aclService;
	private final ExportedFilesService  exportedFilesService;
	private final GpsLocationRepository gpsLocationRepository;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Transactional
	protected List<Long> processOriginalDirectory(Path leafDir, StringBuilder errorMessage, ArrayList<String> failedFiles,
												  ArrayList<FileResponse> successfulFileResponses) {
		var resultList = new ArrayList<Long>(2);
		resultList.add(0L); // scannedFilesCount
		resultList.add(0L); // newFilesCount
		var files = leafDir.toFile()
						   .listFiles();

		if (files == null || files.length == 0) {
			log.warn("Directory is empty: {}", leafDir);
			return resultList;
		}

		// Check if the file group already exists
		var relativeDirectory = computeRelativeFilePath(originalRootDirectory, leafDir.toFile());
		var groupName = leafDir.getFileName()
							   .toString();

		FileGroupEntity fileGroup;
		var             optionalExistingGroup = fileGroupRepository.findByPathAndGroupName(relativeDirectory, groupName);

		if (optionalExistingGroup.isPresent()) {
			log.info("File group already exists for path {} and group name {}, using existing group", relativeDirectory, groupName);
			// If it exists, we use the existing one
			fileGroup = optionalExistingGroup.get();
		} else {
			fileGroup = fileGroupRepository.save(
					FileGroupEntity.builder()
								   .path(relativeDirectory)
								   .groupName(groupName)
								   .build());
		}

		for (var file : files) {
			resultList.set(0, resultList.getFirst() + 1); // Increment scannedFilesCount

			try {
				var processed = processOriginalFile(file, fileGroup);

				if (processed != null && processed) {
					resultList.set(1, resultList.get(1) + 1); // Increment newFilesCount
					// Retrieve the saved FileEntity and convert it to FileResponse.
					var optionalFileEntity = fileRepository.findByFilePathAndFilename(relativeDirectory, file.getName());

					if (optionalFileEntity.isPresent()) {
						// First we reset the metadataRaw field to null so that it slims down the response size.
						var fileResponse = optionalFileEntity.get()
															 .toResponse();
						fileResponse.setMetadataRaw(null);
						successfulFileResponses.add(fileResponse);
					}
				} else if (processed != null) {
					failedFiles.add(file.getName());
				}
			} catch (IOException e) {
				log.error("Error processing file: {}", file.getAbsolutePath(), e);
				errorMessage.append("Error processing file: ")
							.append(file.getAbsolutePath())
							.append(" - ")
							.append(e.getMessage())
							.append("\n");
			}
		}

		return resultList;
	}

	@Transactional
	protected Boolean processOriginalFile(File file, FileGroupEntity fileGroup) throws IOException {
		log.info("Processing file: {}", file.getAbsolutePath());

		var sha256sum        = computeSha256(file);
		var relativeFilePath = computeRelativeFilePath(originalRootDirectory, file);

		// Check first if it already exists in the database
		log.info("Checking if file already exists in the database: {} in path {}", file.getName(), relativeFilePath);
		var optionalExistingFile = fileRepository.findByFilePathAndFilename(relativeFilePath, file.getName());

		if (optionalExistingFile.isPresent()) {
			log.warn("File already exists in the database: {} in path {}, skipping", file.getName(), relativeFilePath);
			var existingFile = optionalExistingFile.get();
			// Next check if the sha256sum matches
			if (existingFile.getSha256sum()
							.equals(sha256sum)) {
				log.info("File has already been scanned to the database: {}", file.getName());
				return null;
			} else {
				log.info("Original file with same path and name but different content already exists in the database, removing it: {} / {}", relativeFilePath,
						 file.getName());
				// Remove the existing file so that it can be reprocessed
				fileRepository.delete(existingFile);
			}
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
			return Boolean.FALSE;
		}

		// Convert the metadata string to a JSON object
		var jsonObject = metadataToJsonObject(metadata);

		if (jsonObject == null) {
			log.error("Failed to convert metadata from string to JSON object: {}", metadata);
			return Boolean.FALSE;
		}

		var mimetype = Files.probeContentType(file.toPath());
		log.info("Probed MIME type: {}", mimetype);

		if (mimetype == null) {
			log.warn("Could not determine MIME type for file: {} by probing. Using exiftool to probe it", file.getName());
			mimetype = extractMimetype(jsonObject);

			if (mimetype == null) {
				log.error("Failed to determine MIME type for file: {}", file.getName());
				return Boolean.FALSE;
			}
		}

		var fileType = determineFileType(mimetype);
		log.info("Determined file type: {}", fileType);

		if (fileType == FileTypeEnum.OTHER) {
			log.warn("Unsupported file type: {}", file.getName());
			return Boolean.FALSE;
		}

		var fileEntity = createFileEntity(fileType, file, fileGroup, mimetype, jsonObject, metadata, relativeFilePath);

		if (fileEntity == null) {
			log.error("Failed to create file entity for file: {}", file.getName());
			return Boolean.FALSE;
		}

		switch (fileType) {
			case IMAGE -> {
				var imageFile = (ImageFileEntity) fileEntity;
				log.info("Extracting resolution and metadata for image file: {}", file);
				var res = extractImageResolution(jsonObject);

				if (res != null) {
					imageFile.setWidth(res.width);
					imageFile.setHeight(res.height);
				}

				imageFile.setColorDepth(extractImageColorDepth(jsonObject));
				imageFile.setDpi(extractImageDpi(jsonObject));
				imageFile.setGroupLabel(extractLabel(jsonObject));
				fileRepository.save(imageFile);
			}
			case VIDEO -> {
				var videoFile = (VideoFileEntity) fileEntity;
				var res = extractXYResolution(file);
				videoFile.setWidth(res.width);
				videoFile.setHeight(res.height);
				videoFile.setFrameRate(extractFrameRate(file));
				videoFile.setDuration(extractAudioVideoDuration(file));
				videoFile.setCodec(extractVideoCodec(file));
				fileRepository.save(videoFile);
			}
			case AUDIO -> {
				var audioFile = (AudioFileEntity) fileEntity;
				audioFile.setDuration(extractAudioVideoDuration(file));
				audioFile.setBitRate(extractAudioBitRate(file));
				audioFile.setSampleRate(extractAudioSampleRate(file));
				audioFile.setCodec(extractAudioCodec(file));
				audioFile.setChannels(extractAudioChannels(file));
				fileRepository.save(audioFile);
			}
			case DOCUMENT -> {
				var documentFile = (DocumentFileEntity) fileEntity;
				documentFile.setPageCount(extractDocumentPageCount(file));
				documentFile.setFormat(extractDocumentFormat(file));
				fileRepository.save(documentFile);
			}
			case VECTOR -> {
				var vectorFile = (VectorFileEntity) fileEntity;
				var res = extractXYResolution(file);
				vectorFile.setWidth(res.width);
				vectorFile.setHeight(res.height);
				vectorFile.setLayersCount(extractVectorLayersCount(file));
				fileRepository.save(vectorFile);
			}
			case ICON -> {
				var iconFile = (IconFileEntity) fileEntity;
				var res = extractXYResolution(file);
				iconFile.setWidth(res.width);
				iconFile.setHeight(res.height);
				iconFile.setIsScalable(extractIconIsScalable(file));
				fileRepository.save(iconFile);
			}
			case FONT -> {
				var fontFile = (FontFileEntity) fileEntity;
				fontFile.setFontFamily(extractFontFamily(file));
				fontFile.setWeight(extractFontWeight(file));
				fontFile.setStyle(extractFontStyle(file));
				fileRepository.save(fontFile);
			}
			case ARCHIVE -> {
				var archiveFile = (ArchiveFileEntity) fileEntity;
				archiveFile.setCompressionMethod(extractArchiveCompressionMethod(file));
				archiveFile.setUncompressedSize(extractArchiveUncompressedSize(file));
				archiveFile.setContentCount(extractArchiveContentCount(file));
				archiveFile.setIsEncrypted(extractArchiveIsEncrypted(file));
				fileRepository.save(archiveFile);
			}
			default -> {
				return Boolean.FALSE;
			}
		}

		saveTags(jsonObject, fileEntity);
		processMetadata(jsonObject, fileEntity);
		return Boolean.TRUE;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected List<Long> processExportDirectory(Path leafDir, StringBuilder errorMessage, ArrayList<String> orphanedFiles,
												ArrayList<ExportFileResponse> successfulFileResponses) {
		var resultList = new ArrayList<Long>(2);
		resultList.add(0L); // scannedFilesCount
		resultList.add(0L); // newFilesCount

		var files = leafDir.toFile()
						   .listFiles();

		if (files == null || files.length == 0) {
			log.warn("Derivative directory is empty: {}", leafDir);
			return resultList;
		}

		for (var file : files) {
			resultList.set(0, resultList.getFirst() + 1); // Increment scannedFilesCount
			var relativeFilePath = computeRelativeFilePath(exportRootDirectory, file);
			var sha256sum        = computeSha256(file);

			// Check first if the file already exists in the database
			var optionalExportFile = exportFileRepository.findByFilePathAndFilename(relativeFilePath, file.getName());

			if (optionalExportFile.isPresent()) {
				var exportFile = optionalExportFile.get();
				log.info("Exported file already exists in the database: {} in path {}, skipping", file.getName(), relativeFilePath);

				// If the sha256sum matches, we skip it
				if (sha256sum != null
					&& sha256sum.equals(exportFile.getSha256sum())) {
					continue;
				}

				// If the sha256sum does not match, we remove the existing entry so that it can be reprocessed
				log.info("Export file with same path and name but different content already exists in the database, removing it: {} / {}", relativeFilePath,
						 file.getName());
				exportFileRepository.delete(exportFile);
			}

			JSONObject metadataObject;

			try {
				metadataObject = extractMetadataJsonObject(file);
			} catch (IOException e) {
				errorMessage.append("Failed to extract metadata from exported file: ")
							.append(file.getAbsolutePath())
							.append(" - ")
							.append(e.getMessage())
							.append("\n");
				log.error("Failed to extract metadata from exported file: {}", file.getAbsolutePath(), e);
				throw new RuntimeException(e);
			}

			var originalDocumentId = extractOriginalDocumentId(metadataObject);

			if (exportedFilesService.existsByOriginalDocumentId(originalDocumentId)
				|| exportedFilesService.existsByPathAndFilename(file.getPath(), file.getName())) {
				log.info("Found already registered exported file at {}: {}", originalDocumentId, file.getName());
				continue;
			}

			resultList.set(1, resultList.get(1) + 1); // Increment newFilesCount

			// Search for the original file using the document ID
			var originalFileEntity = fileRepository.findByOriginalDocumentId(originalDocumentId);

			if (originalFileEntity == null) {
				log.warn("No original file found for exported file: {}", file.getName());
				orphanedFiles.add(file.getName());
				continue; // Skip processing this file
			}

			// Finally save the exported file entity
			// First get the sha256sum of the exported file
			var mimetype = extractMimetype(metadataObject);

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

		return resultList;
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

		var description            = extractDescription(jsonObject);
		var originalDateTimeString = extractOriginalDateTime(jsonObject);
		var originalDateTime       = dateTimeParser(originalDateTimeString);
		var originalSecondFraction = extractOriginalSecondFraction(jsonObject);
		var originalDocumentId     = extractOriginalDocumentId(jsonObject);
		var rightsHolder           = extractRightsHolder(jsonObject);
		var rightsTerms            = extractRightsTerms(jsonObject);
		var rightsUrl              = extractRightsUrl(jsonObject);
		var creatorName            = extractCreatorName(jsonObject);
		var creatorEmail           = extractCreatorEmail(jsonObject);
		var creatorCountry         = extractCreatorCountry(jsonObject);
		var creatorUrl             = extractCreatorUrl(jsonObject);
		var gpsData                = extractGpsData(jsonObject);
		var gpsTimestamp           = extractGpsTime(jsonObject);

		// Check if the GPS data already exists in the database
		// Look up the existing GPS data by comparing the latitude, latitude ref, longitude and longitude ref
		var optionalExistingGps = gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(
				gpsData.getLatitude(),
				gpsData.getLatitudeRef(),
				gpsData.getLongitude(),
				gpsData.getLongitudeRef());

		if (optionalExistingGps.isEmpty()) {
			// Make sure the non-null fields are not null
			if (gpsData.getLatitude() == null
				|| gpsData.getLatitudeRef() == null
				|| gpsData.getLongitude() == null
				|| gpsData.getLongitudeRef() == null) {
				log.warn("GPS data not containing required fields, cannot save GPS data for file: {}", gpsData);
			} else {
				log.debug("New GPS data containing required fields, saving to file: {}", gpsData);
				gpsData = gpsLocationRepository.save(gpsData);
			}
		} else {
			var existingGps    = optionalExistingGps.get();
			log.debug("Using already existing GPS data found in the database, index: {}", existingGps.getId());
			var updateExisting = false;
			// See if we now have the location data which previously was null, if so, then update the existing entry
			if (gpsData.getCountry() != null
				&& existingGps.getCountry() == null) {
				existingGps.setCountry(gpsData.getCountry());
				updateExisting = true;
			}

			if (gpsData.getCity() != null
				&& existingGps.getCity() == null) {
				existingGps.setCity(gpsData.getCity());
				updateExisting = true;
			}

			if (gpsData.getState() != null
				&& existingGps.getState() == null) {
				existingGps.setState(gpsData.getState());
				updateExisting = true;
			}

			if (gpsData.getStreet() != null
				&& existingGps.getStreet() == null) {
				existingGps.setStreet(gpsData.getStreet());
				updateExisting = true;
			}

			if (gpsData.getSubLocation() != null
				&& existingGps.getSubLocation() == null) {
				existingGps.setSubLocation(gpsData.getSubLocation());
				updateExisting = true;
			}

			if (updateExisting) {
				gpsData = gpsLocationRepository.save(existingGps);
				log.debug("Updated existing GPS data with new location information: {}", existingGps);
			} else {
				gpsData = existingGps;
			}
		}

		if (originalDocumentId != null) {
			var existingFile = fileRepository.findByOriginalDocumentId(originalDocumentId);

			if (existingFile != null) {
				log.warn("File with the same original document ID already exists in the database: {} for file: {}", originalDocumentId, file.getName());
				return null;
			}
		}

		var aclId = 0L;

		try {
			aclId = aclService.createNewAcl(userId, null, true, true, true, true);
		} catch (VempainAclException e) {
			throw new VempainRuntimeException();
		}

		// Instantiate the correct subclass (no common field setting here)
		FileEntity entity = switch (fileType) {
			case IMAGE -> new ImageFileEntity();
			case VIDEO -> new VideoFileEntity();
			case AUDIO -> new AudioFileEntity();
			case DOCUMENT -> new DocumentFileEntity();
			case VECTOR -> new VectorFileEntity();
			case ICON -> new IconFileEntity();
			case FONT -> new FontFileEntity();
			case ARCHIVE -> new ArchiveFileEntity();
			case OTHER -> throw new IllegalArgumentException("Unsupported file type for file: " + file.getName());
		};

		// Preserve original externalFileId logic (IMAGE had a dash, others not)
		String externalFileId = (fileType == FileTypeEnum.IMAGE)
								? fileType + "-" + sha256sum
								: fileType + sha256sum;

		// Populate shared FileEntity fields once
		entity.setAclId(aclId);
		entity.setCreated(Instant.now());
		entity.setCreator(userId);
		entity.setCreatorCountry(creatorCountry);
		entity.setCreatorEmail(creatorEmail);
		entity.setCreatorName(creatorName);
		entity.setCreatorUrl(creatorUrl);
		entity.setDescription(description);
		entity.setExternalFileId(externalFileId);
		entity.setFileGroup(fileGroup);
		entity.setFilePath(relativeFilePath);
		entity.setFileType(fileType.name());
		entity.setFilename(file.getName());
		entity.setFilesize(file.length());
		entity.setGpsLocationId(gpsData.getId());
		entity.setGpsTimestamp(gpsTimestamp);
		entity.setMetadataRaw(metadata);
		entity.setMimetype(mimetype);
		entity.setOriginalDatetime(originalDateTime);
		entity.setOriginalDocumentId(originalDocumentId);
		entity.setOriginalSecondFraction(originalSecondFraction);
		entity.setRightsHolder(rightsHolder);
		entity.setRightsTerms(rightsTerms);
		entity.setRightsUrl(rightsUrl);
		entity.setSha256sum(sha256sum);

		return entity;
	}

	@Transactional
	protected void saveTags(JSONObject jsonObject, FileEntity fileEntity) {
		var subjects = extractSubjects(jsonObject);

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
}
