package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.exception.VempainAclException;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.auth.exception.VempainRuntimeException;
import fi.poltsi.vempain.auth.service.AclService;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.ArchiveFileEntity;
import fi.poltsi.vempain.file.entity.AudioFileEntity;
import fi.poltsi.vempain.file.entity.BinaryFileEntity;
import fi.poltsi.vempain.file.entity.DataFileEntity;
import fi.poltsi.vempain.file.entity.DocumentFileEntity;
import fi.poltsi.vempain.file.entity.ExecutableFileEntity;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.entity.FileTag;
import fi.poltsi.vempain.file.entity.FontFileEntity;
import fi.poltsi.vempain.file.entity.IconFileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.InteractiveFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import fi.poltsi.vempain.file.entity.TagEntity;
import fi.poltsi.vempain.file.entity.ThumbFileEntity;
import fi.poltsi.vempain.file.entity.VectorFileEntity;
import fi.poltsi.vempain.file.entity.VideoFileEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.FileTagRepository;
import fi.poltsi.vempain.file.repository.GpsLocationRepository;
import fi.poltsi.vempain.file.repository.MetadataRepository;
import fi.poltsi.vempain.file.repository.TagRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
								   .description("")
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

	private FileEntity createFileEntity(FileTypeEnum fileTypeEnum, File file, FileGroupEntity fileGroup, String mimetype, JSONObject jsonObject, String metadata,
										String relativeFilePath) {
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

		// Handle GPS data safely: only persist / associate if required coordinate fields exist.
		if (gpsData != null) {
			boolean hasRequired = gpsData.getLatitude() != null &&
								  gpsData.getLatitudeRef() != null &&
								  gpsData.getLongitude() != null &&
								  gpsData.getLongitudeRef() != null;

			if (hasRequired) {
				// Look up existing GPS entry
				var optionalExistingGps = gpsLocationRepository.findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(
						gpsData.getLatitude(),
						gpsData.getLatitudeRef(),
						gpsData.getLongitude(),
						gpsData.getLongitudeRef());

				if (optionalExistingGps.isEmpty()) {
					log.debug("Persisting new GPS data for file: {}", file.getName());
					gpsData = gpsLocationRepository.save(gpsData);
				} else {
					var     existingGps    = optionalExistingGps.get();
					boolean updateExisting = false;

					// Enrich existing GPS record with newly available location fields
					if (gpsData.getCountry() != null && existingGps.getCountry() == null) {
						existingGps.setCountry(gpsData.getCountry());
						updateExisting = true;
					}
					if (gpsData.getCity() != null && existingGps.getCity() == null) {
						existingGps.setCity(gpsData.getCity());
						updateExisting = true;
					}
					if (gpsData.getState() != null && existingGps.getState() == null) {
						existingGps.setState(gpsData.getState());
						updateExisting = true;
					}
					if (gpsData.getStreet() != null && existingGps.getStreet() == null) {
						existingGps.setStreet(gpsData.getStreet());
						updateExisting = true;
					}
					if (gpsData.getSubLocation() != null && existingGps.getSubLocation() == null) {
						existingGps.setSubLocation(gpsData.getSubLocation());
						updateExisting = true;
					}

					if (updateExisting) {
						gpsData = gpsLocationRepository.save(existingGps);
						log.debug("Updated existing GPS data with new location info (id={}): {}", existingGps.getId(), existingGps);
					} else {
						gpsData = existingGps;
						log.debug("Using existing GPS data (id={}): {}", existingGps.getId(), existingGps);
					}
				}
			} else {
				// Missing required coordinate fields -> do not associate transient instance
				log.debug("GPS data missing required coordinate fields, skipping association for file: {}", file.getName());
				gpsData = null;
			}
		}

		if (originalDocumentId != null) {
			var existingFile = fileRepository.findByOriginalDocumentId(originalDocumentId);

			if (existingFile != null) {
				log.warn("File with the same original document ID already exists in the database: {} as file file: {} / {}", originalDocumentId,
						 existingFile.getFilePath(), existingFile.getFilename());
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
		FileEntity entity = switch (fileTypeEnum) {
			case ARCHIVE -> new ArchiveFileEntity();
			case AUDIO -> new AudioFileEntity();
			case BINARY -> new BinaryFileEntity();
			case DATA -> new DataFileEntity();
			case DOCUMENT -> new DocumentFileEntity();
			case EXECUTABLE -> new ExecutableFileEntity();
			case FONT -> new FontFileEntity();
			case ICON -> new IconFileEntity();
			case IMAGE -> new ImageFileEntity();
			case INTERACTIVE -> new InteractiveFileEntity();
			case THUMB -> new ThumbFileEntity();
			case VECTOR -> new VectorFileEntity();
			case VIDEO -> new VideoFileEntity();
			case UNKNOWN -> throw new IllegalArgumentException("Unsupported file type for file: " + file.getName());
		};

		// Preserve original externalFileId logic (IMAGE had a dash, others not)
		String externalFileId = (fileTypeEnum == FileTypeEnum.IMAGE)
								? fileTypeEnum + "-" + sha256sum
								: fileTypeEnum + sha256sum;

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
		entity.setFilePath(relativeFilePath);
		entity.setFileType(fileTypeEnum);
		entity.setFilename(file.getName());
		entity.setFilesize(file.length());
		entity.setGpsLocation(gpsData);
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

	// Helper to safely link a persisted file to its group
	private void linkFileToGroup(FileEntity entity, FileGroupEntity group) {
		if (group.getFiles() == null) {
			group.setFiles(new HashSet<>());
		}
		if (entity.getFileGroups() == null) {
			entity.setFileGroups(new HashSet<>());
		}
		// Owning side: FileGroupEntity.files
		if (!group.getFiles()
				  .contains(entity)) {
			group.getFiles()
				 .add(entity);
		}
		if (!entity.getFileGroups()
				   .contains(group)) {
			entity.getFileGroups()
				  .add(group);
		}
		// Persist join table row
		fileGroupRepository.save(group);
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

		var fileTypeEnum = FileTypeEnum.getFileTypeByMimetype(mimetype);
		log.info("Determined file type: {}", fileTypeEnum);

		if (fileTypeEnum == FileTypeEnum.UNKNOWN) {
			log.warn("Unsupported file type: {}", file.getName());
			return Boolean.FALSE;
		}

		var fileEntity = createFileEntity(fileTypeEnum, file, fileGroup, mimetype, jsonObject, metadata, relativeFilePath);

		if (fileEntity == null) {
			log.warn("Can not create file entity for file: {}", file.getName());
			return Boolean.FALSE;
		}

		switch (fileTypeEnum) {
			case ARCHIVE -> {
				var archiveFile = (ArchiveFileEntity) fileEntity;
				archiveFile.setCompressionMethod(extractArchiveCompressionMethod(file));
				archiveFile.setUncompressedSize(extractArchiveUncompressedSize(file));
				archiveFile.setContentCount(extractArchiveContentCount(file));
				archiveFile.setIsEncrypted(extractArchiveIsEncrypted(file));
				fileRepository.save(archiveFile);
				linkFileToGroup(archiveFile, fileGroup);
			}
			case AUDIO -> {
				var audioFile = (AudioFileEntity) fileEntity;
				audioFile.setDuration(extractAudioVideoDuration(file));
				audioFile.setBitRate(extractAudioBitRate(file));
				audioFile.setSampleRate(extractAudioSampleRate(file));
				audioFile.setCodec(extractAudioCodec(file));
				audioFile.setChannels(extractAudioChannels(file));
				fileRepository.save(audioFile);
				linkFileToGroup(audioFile, fileGroup);
			}
			case BINARY -> {
				var binary = (BinaryFileEntity) fileEntity;
				// Try to infer software name from metadata label or fallback to null
				var label = extractLabel(jsonObject);
				binary.setSoftwareName(label);
				binary.setSoftwareMajorVersion(parseMajorVersionFromText(label != null ? label : file.getName()));
				fileRepository.save(binary);
				linkFileToGroup(binary, fileGroup);
			}
			case DATA -> {
				var data = (DataFileEntity) fileEntity;
				data.setDataStructure(determineDataStructure(mimetype));
				fileRepository.save(data);
				linkFileToGroup(data, fileGroup);
			}
			case DOCUMENT -> {
				var documentFile = (DocumentFileEntity) fileEntity;
				documentFile.setPageCount(extractDocumentPageCount(file));
				documentFile.setFormat(extractDocumentFormat(file));
				fileRepository.save(documentFile);
				linkFileToGroup(documentFile, fileGroup);
			}
			case EXECUTABLE -> {
				var exe = (ExecutableFileEntity) fileEntity;
				var os  = determineOperatingSystems(mimetype, file.getName());
				exe.setOperatingSystems(os);
				exe.setScript(isScript(mimetype, file.getName()));
				fileRepository.save(exe);
				linkFileToGroup(exe, fileGroup);
			}
			case FONT -> {
				var fontFile = (FontFileEntity) fileEntity;
				fontFile.setFontFamily(extractFontFamily(file));
				fontFile.setWeight(extractFontWeight(file));
				fontFile.setStyle(extractFontStyle(file));
				fileRepository.save(fontFile);
				linkFileToGroup(fontFile, fileGroup);
			}
			case ICON -> {
				var iconFile = (IconFileEntity) fileEntity;
				var res      = extractXYResolution(file);
				iconFile.setWidth(res.width);
				iconFile.setHeight(res.height);
				iconFile.setIsScalable(extractIconIsScalable(file));
				fileRepository.save(iconFile);
				linkFileToGroup(iconFile, fileGroup);
			}
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
				linkFileToGroup(imageFile, fileGroup);
			}
			case INTERACTIVE -> {
				var inter = (InteractiveFileEntity) fileEntity;
				inter.setTechnology(determineInteractiveTechnology(mimetype));
				fileRepository.save(inter);
				linkFileToGroup(inter, fileGroup);
			}
			case THUMB -> {
				var thumb = (ThumbFileEntity) fileEntity;
				thumb.setRelationType("thumbnail");
				// Try to link to original file via originalDocumentId if present
				if (fileEntity.getOriginalDocumentId() != null) {
					var target = fileRepository.findByOriginalDocumentId(fileEntity.getOriginalDocumentId());
					if (target != null) {
						thumb.setTargetFile(target);
					}
				}
				fileRepository.save(thumb);
				linkFileToGroup(thumb, fileGroup);
			}
			case VECTOR -> {
				var vectorFile = (VectorFileEntity) fileEntity;
				var res = extractXYResolution(file);
				vectorFile.setWidth(res.width);
				vectorFile.setHeight(res.height);
				vectorFile.setLayersCount(extractVectorLayersCount(file));
				fileRepository.save(vectorFile);
				linkFileToGroup(vectorFile, fileGroup);
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
				linkFileToGroup(videoFile, fileGroup);
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

	// --- Helpers for new file types ---

	private Integer parseMajorVersionFromText(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		// Find first number sequence as a major version, optionally followed by dots (e.g. "Name 12.3")
		for (String token : text.split("[^0-9.]")) {
			if (token == null || token.isBlank()) {
				continue;
			}
			try {
				// Take the part before first dot as major
				var majorStr = token.split("\\.")[0];
				if (!majorStr.isBlank()) {
					return Integer.parseInt(majorStr);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}

	private String determineDataStructure(String mimetype) {
		if (mimetype == null) {
			return "OTHER";
		}
		var mt = mimetype.toLowerCase();
		if (mt.contains("json")) {
			return "JSON";
		}
		if (mt.contains("xml")) {
			return "XML";
		}
		if (mt.contains("ndjson")) {
			return "NDJSON";
		}
		if (mt.contains("yaml")) {
			return "YAML";
		}
		if (mt.contains("csv")) {
			return "CSV";
		}
		if (mt.contains("octet-stream") || mt.contains("x-binary")) {
			return "BINARY";
		}
		return "OTHER";
	}

	private Set<String> determineOperatingSystems(String mimetype, String filename) {
		var result = new HashSet<String>();
		var mt     = mimetype != null ? mimetype.toLowerCase() : "";
		var name   = filename != null ? filename.toLowerCase() : "";

		if (mt.contains("x-ms") || name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".msi") || name.endsWith(".cmd")) {
			result.add("WINDOWS");
		}
		if (mt.contains("x-executable") || name.endsWith(".run") || name.endsWith(".bin")) {
			result.add("LINUX");
		}
		if (mt.contains("java-archive") || name.endsWith(".jar")) {
			result.add("JVM");
		}
		if (mt.contains("vnd.android.package-archive") || name.endsWith(".apk")) {
			result.add("ANDROID");
		}
		if (name.endsWith(".app") || mt.contains("mac") || mt.contains("x-mach-o")) {
			result.add("MACOS");
		}

		if (result.isEmpty()) {
			result.add("OTHER");
		}
		return result;
	}

	private boolean isScript(String mimetype, String filename) {
		var mt   = mimetype != null ? mimetype.toLowerCase() : "";
		var name = filename != null ? filename.toLowerCase() : "";
		return mt.contains("shellscript") ||
			   mt.contains("x-sh") ||
			   name.endsWith(".sh") ||
			   name.endsWith(".bat") ||
			   name.endsWith(".cmd") ||
			   name.endsWith(".ps1") ||
			   name.endsWith(".py") ||
			   name.endsWith(".pl");
	}

	private String determineInteractiveTechnology(String mimetype) {
		if (mimetype == null) {
			return null;
		}
		var mt = mimetype.toLowerCase();
		if (mt.contains("x-shockwave-flash")) {
			return "FLASH";
		}
		if (mt.contains("x-director")) {
			return "SHOCKWAVE";
		}
		return "OTHER";
	}
}
