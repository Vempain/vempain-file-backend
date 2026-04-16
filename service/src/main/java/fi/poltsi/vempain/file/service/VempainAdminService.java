package fi.poltsi.vempain.file.service;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.feign.VempainAdminFileClient;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

@Slf4j
@RequiredArgsConstructor
@Service
public class VempainAdminService {
	private final VempainAdminFileIngestClient vempainAdminFileIngestClient;
	private final VempainAdminFileClient vempainAdminFileClient;
	private final ObjectMapper objectMapper;

	public FileIngestResponse uploadAsSiteFile(File exportedFile, FileIngestRequest fileIngestRequest) {
		var multiPartFile = VempainMultipartFile.builder()
												.path(exportedFile.toPath())
												.contentType(fileIngestRequest.getMimeType())
												.build();
		String fileIngestRequestString;

		fileIngestRequestString = objectMapper.writeValueAsString(fileIngestRequest);

		log.debug("Uploading file {} to Vempain Admin service", exportedFile.getAbsolutePath());

		try {
			var responseEntity = vempainAdminFileIngestClient.ingest(fileIngestRequestString, multiPartFile);
			if (responseEntity == null || !responseEntity.getStatusCode()
														 .is2xxSuccessful()) {
				log.error("File upload to Vempain admin failed with HTTP status {}", responseEntity != null ? responseEntity.getStatusCode() : "null");
				throw new VempainAuthenticationException();
			}
			log.debug("File upload successful: {}", responseEntity.getBody());
			return responseEntity.getBody();
		} catch (FeignException e) {
			if (e.status() == 403) {
				log.warn("File upload failed due to Forbidden (403). Triggering re-authentication.");
				throw new VempainAuthenticationException();
			}

			log.error("File upload failed with FeignException (status {}): {}", e.status(), e.getMessage());
			throw e;
		}
	}

	public PagedResponse<SiteFileResponse> getPageableSiteFiles(FileTypeEnum fileType,
	                                                            int pageNumber,
	                                                            int pageSize,
	                                                            String sortBy,
	                                                            Sort.Direction direction,
	                                                            String filter,
	                                                            String filterColumn) {
		try {
			var responseEntity = vempainAdminFileClient.getPageableSiteFiles(fileType, pageNumber, pageSize, sortBy, direction, filter, filterColumn);
			if (responseEntity == null || !responseEntity.getStatusCode()
			                                             .is2xxSuccessful()) {
				HttpStatusCode status = responseEntity != null ? responseEntity.getStatusCode() : null;
				log.warn("Failed to fetch pageable site files. Status: {}", status);
				return null;
			}
			return responseEntity.getBody();
		} catch (FeignException e) {
			log.warn("Failed to fetch pageable site files from admin backend (status={}): {}", e.status(), e.getMessage());
			return null;
		}
	}
}
