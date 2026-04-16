package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.SchedulerCheckpointEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.SchedulerCheckpointRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static fi.poltsi.vempain.file.tools.FileTool.computeSha256;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMetadataJsonObject;
import static fi.poltsi.vempain.file.tools.MetadataTool.extractMimetype;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatedFileRefreshSchedulerService {

	private static final String TASK_NAME = "updated_file_refresh";

	private final FileRepository                fileRepository;
	private final ExportFileRepository          exportFileRepository;
	private final SchedulerCheckpointRepository schedulerCheckpointRepository;
	private final DirectoryProcessorService     directoryProcessorService;
	private final PublishService                publishService;
	private final VempainAdminService           vempainAdminService;

	@Value("${vempain.refresh-updated-files.enabled:true}")
	private boolean schedulerEnabled;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	@Value("${vempain.export-file-type:jpeg}")
	private String exportFileType;

	@Scheduled(cron = "${vempain.refresh-updated-files.cron:0 */10 * * * *}")
	public void refreshUpdatedFilesScheduled() {
		if (!schedulerEnabled) {
			return;
		}
		runRefresh();
	}

	public void runRefresh() {
		var runStartedAt = Instant.now();
		var checkpoint   = schedulerCheckpointRepository.findById(TASK_NAME)
		                                                .orElse(null);
		var lastCheckAt  = checkpoint != null ? checkpoint.getLastChecked() : Instant.EPOCH;
		var firstRun     = checkpoint == null;

		log.info("Starting updated file refresh. firstRun={}, lastCheckAt={}", firstRun, lastCheckAt);

		for (var fileEntity : fileRepository.findAll()) {
			processSingleFile(fileEntity, lastCheckAt, firstRun);
		}

		schedulerCheckpointRepository.save(SchedulerCheckpointEntity.builder()
		                                                            .taskName(TASK_NAME)
		                                                            .lastChecked(runStartedAt)
		                                                            .build());
		log.info("Updated file refresh finished. New checkpoint={}", runStartedAt);
	}

	private void processSingleFile(FileEntity fileEntity, Instant lastCheckAt, boolean firstRun) {
		var sourcePath = resolveOriginalPath(fileEntity);
		if (!Files.exists(sourcePath)) {
			return;
		}

		try {
			var modifiedAt = Files.getLastModifiedTime(sourcePath)
			                      .toInstant();
			if (!firstRun && !modifiedAt.isAfter(lastCheckAt)) {
				return;
			}
		} catch (Exception e) {
			log.warn("Unable to read last-modified time for file {}", sourcePath, e);
			return;
		}

		var sitePublished = fileEntity.getSiteFilePublished();
		if (sitePublished == null) {
			sitePublished = detectSiteFilePublication(fileEntity);
			fileEntity.setSiteFilePublished(sitePublished);
			fileRepository.save(fileEntity);
		}

		var currentSha = computeSha256(sourcePath.toFile());
		if (currentSha == null || Objects.equals(currentSha, fileEntity.getSha256sum())) {
			return;
		}

		log.info("Refreshing updated file id={} path={}", fileEntity.getId(), sourcePath);
		try {
			var updated = directoryProcessorService.refreshExistingOriginalFile(fileEntity, sourcePath.toFile());
			if (!updated) {
				return;
			}
			refreshLinkedExport(fileEntity.getId());
			if (Boolean.TRUE.equals(sitePublished)) {
				var refreshedEntity = fileRepository.findById(fileEntity.getId())
				                                    .orElse(fileEntity);
				publishService.republishSiteFile(refreshedEntity);
			}
		} catch (Exception e) {
			log.error("Failed to refresh updated file id={} path={}", fileEntity.getId(), sourcePath, e);
		}
	}

	private boolean detectSiteFilePublication(FileEntity fileEntity) {
		if (fileEntity.getFileType() == FileTypeEnum.UNKNOWN) {
			return false;
		}

		for (String filenameCandidate : resolveSiteFileNameCandidates(fileEntity)) {
			for (String filterColumn : List.of("file_name", "filename")) {
				PagedResponse<fi.poltsi.vempain.admin.api.response.file.SiteFileResponse> response =
						vempainAdminService.getPageableSiteFiles(fileEntity.getFileType(), 0, 50, "id", Sort.Direction.ASC, filenameCandidate, filterColumn);
				if (response == null || response.getContent() == null || response.getContent()
				                                                                 .isEmpty()) {
					continue;
				}

				var expectedPath = normalizePath(fileEntity.getFilePath());
				boolean match = response.getContent()
				                        .stream()
				                        .anyMatch(siteFile -> Objects.equals(normalizePath(siteFile.getFilePath()), expectedPath)
				                                              && Objects.equals(siteFile.getFileName(), filenameCandidate));
				if (match) {
					return true;
				}
			}
		}

		return false;
	}

	private void refreshLinkedExport(Long fileId) {
		var optionalExport = exportFileRepository.findByFileId(fileId);
		if (optionalExport.isEmpty()) {
			return;
		}

		var exportFile = optionalExport.get();
		var exportPath = resolveExportPath(exportFile);
		if (!Files.exists(exportPath)) {
			return;
		}

		var sha = computeSha256(exportPath.toFile());
		if (sha == null) {
			return;
		}

		try {
			exportFile.setSha256sum(sha);
			exportFile.setFilesize(Files.size(exportPath));
			exportFile.setMimetype(extractMimetype(extractMetadataJsonObject(exportPath.toFile())));
			exportFile.setOriginalDocumentId(exportFile.getFile()
			                                           .getOriginalDocumentId());
			exportFileRepository.save(exportFile);
		} catch (Exception e) {
			log.warn("Failed to refresh linked export file for file id {}", fileId, e);
		}
	}

	private Path resolveOriginalPath(FileEntity fileEntity) {
		var relativePath = normalizePath(fileEntity.getFilePath());
		return Path.of(originalRootDirectory)
		           .resolve(relativePath)
		           .resolve(fileEntity.getFilename());
	}

	private Path resolveExportPath(ExportFileEntity exportFileEntity) {
		var relativePath = normalizePath(exportFileEntity.getFilePath());
		return Path.of(exportRootDirectory)
		           .resolve(relativePath)
		           .resolve(exportFileEntity.getFilename());
	}

	private String normalizePath(String value) {
		if (value == null || value.isBlank() || "/".equals(value)) {
			return "";
		}
		return value.startsWith("/") ? value.substring(1) : value;
	}

	private List<String> resolveSiteFileNameCandidates(FileEntity fileEntity) {
		var candidates = new ArrayList<String>();
		exportFileRepository.findByFileId(fileEntity.getId())
		                    .map(ExportFileEntity::getFilename)
		                    .ifPresent(candidates::add);
		candidates.add(fileEntity.getFilename());

		if (fileEntity.getFileType() == FileTypeEnum.IMAGE && fileEntity.getFilename() != null) {
			int suffixIndex = fileEntity.getFilename()
			                            .lastIndexOf('.');
			if (suffixIndex > 0) {
				candidates.add(fileEntity.getFilename()
				                         .substring(0, suffixIndex) + "." + exportFileType);
			}
		}

		return candidates.stream()
		                 .filter(Objects::nonNull)
		                 .distinct()
		                 .toList();
	}
}

