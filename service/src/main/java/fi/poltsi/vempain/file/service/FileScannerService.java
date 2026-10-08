package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.TaskTypeEnum;
import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.api.response.ScanExportResponse;
import fi.poltsi.vempain.file.api.response.ScanOriginalResponse;
import fi.poltsi.vempain.file.api.response.ScanResponses;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;
import fi.poltsi.vempain.file.repository.ExportFileRepository;
import fi.poltsi.vempain.file.repository.FileGroupRepository;
import fi.poltsi.vempain.file.repository.files.FileRepository;
import fi.poltsi.vempain.file.task.TaskProgress;
import fi.poltsi.vempain.file.task.TaskRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileScannerService {

	private final DirectoryProcessorService directoryProcessorService;
	private final FileResponseEnricher fileResponseEnricher;
	private final TaskRunner           taskRunner;
	private final FileRepository       fileRepository;
	private final ExportFileRepository exportFileRepository;
	private final FileGroupRepository  fileGroupRepository;

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	/**
	 * Validates the requested directories and starts a background task that scans them. One step per leaf directory; the finished
	 * task carries the {@link ScanResponses} as result.
	 *
	 * @throws org.springframework.web.server.ResponseStatusException when a directory is missing or outside the configured roots
	 */
	public TaskProgress scanDirectoriesAsTask(ScanRequest scanRequest) {
		if (scanRequest.getOriginalDirectory() != null) {
			resolveScanDirectory(originalRootDirectory, scanRequest.getOriginalDirectory());
		}
		if (scanRequest.getExportDirectory() != null) {
			resolveScanDirectory(exportRootDirectory, scanRequest.getExportDirectory());
		}

		var title = "Scan " + String.join(" and ", java.util.stream.Stream.of(scanRequest.getOriginalDirectory(), scanRequest.getExportDirectory())
																		  .filter(java.util.Objects::nonNull)
																		  .toList());
		return taskRunner.submitDurable(TaskTypeEnum.SCAN_DIRECTORIES.name(), title, 0, scanRequest,
										progress -> scanDirectories(scanRequest, progress));
	}

	public ScanResponses scanDirectories(ScanRequest scanRequest) {
		return scanDirectories(scanRequest, null);
	}

	public ScanResponses scanDirectories(ScanRequest scanRequest, TaskProgress progress) {
		var scanResponses = new ScanResponses();
		var recorder = recorderFor(progress);

		if (scanRequest.getOriginalDirectory() != null) {
			var originalResult = scanOriginalDirectory(scanRequest.getOriginalDirectory(), progress, recorder);
			scanResponses.setScanOriginalResponse(originalResult);
		}

		if (scanRequest.getExportDirectory() != null) {
			var exportedResult = scanExportDirectory(scanRequest.getExportDirectory(), progress, recorder);
			scanResponses.setScanExportResponse(exportedResult);
		}

		return scanResponses;
	}

	/**
	 * Recorder that stops the scan at the task's cancellation checkpoints and registers the removal of every created entity as
	 * the undo of the scan. Without a task the recorder does nothing.
	 */
	ScanRecorder recorderFor(TaskProgress progress) {
		if (progress == null) {
			return ScanRecorder.NONE;
		}
		return new ScanRecorder() {
			@Override
			public void checkpoint() {
				progress.checkpoint();
			}

			@Override
			public void fileGroupCreated(FileGroupEntity fileGroup) {
				var id = fileGroup.getId();
				progress.registerCompensation("Remove file group " + fileGroup.getGroupName(), () -> fileGroupRepository.deleteById(id));
			}

			@Override
			public void fileCreated(FileEntity file) {
				var id = file.getId();
				progress.registerCompensation("Remove file " + file.getFilename(), () -> fileRepository.deleteById(id));
			}

			@Override
			public void exportFileCreated(ExportFileEntity exportFile) {
				var id = exportFile.getId();
				progress.registerCompensation("Remove export file " + exportFile.getFilename(), () -> exportFileRepository.deleteById(id));
			}
		};
	}

	protected ScanOriginalResponse scanOriginalDirectory(String selectedDirectory, TaskProgress progress, ScanRecorder recorder) {
		var scannedFilesCount       = 0L;
		var newFilesCount           = 0L;
		var success                 = true;
		var errorMessage            = new StringBuilder();
		var failedFiles             = new ArrayList<String>();
		var successfulFileResponses = new ArrayList<FileResponse>();
		var leafDirectories         = new ArrayList<Path>();
		var scanDirectory = resolveScanDirectory(originalRootDirectory, selectedDirectory);

		success = populateLeafDirectory(leafDirectories, errorMessage, scanDirectory);
		addSteps(progress, leafDirectories.size());

		for (var leafDir : leafDirectories) {
			// Each processDirectory call will run in its own transaction
			var results = directoryProcessorService.processOriginalDirectory(leafDir, errorMessage, failedFiles, successfulFileResponses, recorder);
			step(progress, scanDirectory, leafDir);
			scannedFilesCount += results.get(0);
			newFilesCount += results.get(1);
			success = success && scannedFilesCount == newFilesCount;
		}
		fileResponseEnricher.enrichAll(successfulFileResponses);

		return ScanOriginalResponse.builder()
		                           .success(success)
		                           .scannedFilesCount(scannedFilesCount)
		                           .newFilesCount(newFilesCount)
		                           .failedFiles(failedFiles)
		                           .successfulFiles(successfulFileResponses)
		                           .errorMessage(errorMessage.toString())
								   .build();
	}

	protected ScanExportResponse scanExportDirectory(String exportedDirectory, TaskProgress progress, ScanRecorder recorder) {
		var scannedFilesCount       = 0L;
		var newFilesCount           = 0L;
		var success                 = true;
		var errorMessage            = new StringBuilder();
		var orphanedFiles           = new ArrayList<String>();
		var successfulFileResponses = new ArrayList<ExportFileResponse>();
		var leafDirectories         = new ArrayList<Path>();
		var scanDirectory = resolveScanDirectory(exportRootDirectory, exportedDirectory);

		success = populateLeafDirectory(leafDirectories, errorMessage, scanDirectory);

		if (!success) {
			return ScanExportResponse.builder()
			                         .success(false)
			                         .errorMessage(errorMessage.toString())
									 .build();
		}

		addSteps(progress, leafDirectories.size());
		for (Path leafDir : leafDirectories) {
			var processed = directoryProcessorService.processExportDirectory(leafDir, errorMessage, orphanedFiles, successfulFileResponses, recorder);
			log.debug("Processed {} in export directory", processed.size());
			step(progress, scanDirectory, leafDir);
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

	private static void addSteps(TaskProgress progress, int steps) {
		if (progress != null) {
			progress.setTotalSteps(progress.getTotalSteps()
										   .get() + steps);
		}
	}

	private static void step(TaskProgress progress, Path root, Path leafDirectory) {
		if (progress != null) {
			progress.advance("Scanned " + root.relativize(leafDirectory));
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

	private Path resolveScanDirectory(String configuredRoot, String selectedDirectory) {
		final Path root;
		try {
			root = Path.of(configuredRoot)
			           .toRealPath();
		} catch (IOException e) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
					"Configured scan root is unavailable");
		}
		if (selectedDirectory == null || "/".equals(selectedDirectory)) {
			return root;
		}
		var relativePath = selectedDirectory.startsWith("/") ? selectedDirectory.substring(1) : selectedDirectory;
		var        resolved = root.resolve(relativePath)
		                          .normalize();
		final Path realResolved;
		try {
			realResolved = resolved.toRealPath();
		} catch (IOException e) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST,
					"Selected directory does not exist");
		}
		if (!realResolved.startsWith(root) || Files.isSymbolicLink(resolved)) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST,
					"Selected directory is outside the configured scan root");
		}
		return realResolved;
	}

	private boolean populateLeafDirectory(ArrayList<Path> leafDirectories, StringBuilder errorMessage, Path scanDirectory) {
		try {
			leafDirectories.addAll(Files.walk(scanDirectory)
			                            .filter(Files::isDirectory)
			                            .filter(path -> !Files.isSymbolicLink(path))
			                            .filter(path -> !path.getFileName()
			                                                 .toString()
			                                                 .startsWith("."))
			                            .filter(this::isLeafDirectory)
			                            .toList());
		} catch (IOException e) {
			log.error("Error scanning directory: {}", scanDirectory, e);
			errorMessage.append("Unable to scan the requested directory");
			return false;
		}

		return true;
	}
}
