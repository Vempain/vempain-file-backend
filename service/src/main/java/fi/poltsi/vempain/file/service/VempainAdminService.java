package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@RequiredArgsConstructor
@Service
public class VempainAdminService {
	private final VempainAdminFileIngestClient vempainAdminFileIngestClient;

	public void uploadAsSiteFile(File exportedFile, FileIngestRequest fileIngestRequest) {

		var multiPartFile = new VempainMultipartFile(exportedFile.toPath(), fileIngestRequest.getMimeType());

		log.info("Uploading file {} to Vempain Admin service", exportedFile.getAbsolutePath());
		var responseEntity = vempainAdminFileIngestClient.ingest(fileIngestRequest, multiPartFile);

		if (responseEntity == null || !responseEntity.getStatusCode()
													 .is2xxSuccessful()) {
			log.error("File upload to Vempain admin failed");
			throw new VempainAuthenticationException();
		}

		log.info("File upload successful: {}", responseEntity.getBody());
	}
}
