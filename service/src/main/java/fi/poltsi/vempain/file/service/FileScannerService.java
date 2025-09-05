package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.api.request.ScanRequest;
import fi.poltsi.vempain.file.api.response.ExportFileResponse;
import fi.poltsi.vempain.file.api.response.FileResponse;
import fi.poltsi.vempain.file.api.response.ScanExportResponse;
import fi.poltsi.vempain.file.api.response.ScanOriginalResponse;
import fi.poltsi.vempain.file.api.response.ScanResponses;
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

	@Value("${vempain.original-root-directory}")
	private String originalRootDirectory;

	@Value("${vempain.export-root-directory}")
	private String exportRootDirectory;

	public ScanResponses scanDirectories(ScanRequest scanRequest) {
		var scanResponses = new ScanResponses();

		if (scanRequest.getOriginalDirectory() != null) {
			var originalResult = scanOriginalDirectory(scanRequest.getOriginalDirectory());
			scanResponses.setScanOriginalResponse(originalResult);
		}

		if (scanRequest.getExportDirectory() != null) {
			var exportedResult = scanExportDirectory(scanRequest.getExportDirectory());
			scanResponses.setScanExportResponse(exportedResult);
		}

		return scanResponses;
	}

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
			// Each processDirectory call will run in its own transaction
			var results = directoryProcessorService.processOriginalDirectory(leafDir, errorMessage, failedFiles, successfulFileResponses);
			scannedFilesCount += results.get(0);
			newFilesCount += results.get(1);
			success = success && scannedFilesCount == newFilesCount;
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
			var foo = directoryProcessorService.processExportDirectory(leafDir, errorMessage, orphanedFiles, successfulFileResponses);
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
