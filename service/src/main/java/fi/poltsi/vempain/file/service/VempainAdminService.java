package fi.poltsi.vempain.file.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
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
		var multiPartFile = VempainMultipartFile.builder()
												.path(exportedFile.toPath())
												.contentType(fileIngestRequest.getMimeType())
												.build();
		// Convert request to JSON string
		var    mapper = new ObjectMapper();
		String fileIngestRequestString;

		try {
			fileIngestRequestString = mapper.writeValueAsString(fileIngestRequest);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

		log.info("Uploading file {} to Vempain Admin service", exportedFile.getAbsolutePath());
		// Pass JSON string instead of object
		try {
			var responseEntity = vempainAdminFileIngestClient.ingest(fileIngestRequestString, multiPartFile);
			if (responseEntity == null || !responseEntity.getStatusCode()
														 .is2xxSuccessful()) {
				log.error("File upload to Vempain admin failed with HTTP status {}", responseEntity != null ? responseEntity.getStatusCode() : "null");
				throw new VempainAuthenticationException();
			}

			log.info("File upload successful: {}", responseEntity.getBody());
		} catch (FeignException e) {
			// Distinguish 403 Forbidden -> authentication issue
			if (e.status() == 403) {
				log.warn("File upload failed due to Forbidden (403). Triggering re-authentication.");
				throw new VempainAuthenticationException();
			}
			log.error("File upload failed with FeignException (status {}): {}", e.status(), e.getMessage());
			throw e; // propagate non-auth errors
		}
	}
}
