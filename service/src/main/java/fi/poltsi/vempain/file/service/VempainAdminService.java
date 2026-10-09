package fi.poltsi.vempain.file.service;

import feign.FeignException;
import fi.poltsi.vempain.admin.api.request.file.FileIngestRequest;
import fi.poltsi.vempain.admin.api.request.file.SiteFilePagedRequest;
import fi.poltsi.vempain.admin.api.response.file.FileIngestResponse;
import fi.poltsi.vempain.admin.api.response.file.FileIngestUserResponse;
import fi.poltsi.vempain.admin.api.response.file.SiteFileResponse;
import fi.poltsi.vempain.auth.api.response.PagedResponse;
import fi.poltsi.vempain.auth.exception.VempainAuthenticationException;
import fi.poltsi.vempain.common.api.FileTypeEnum;
import fi.poltsi.vempain.file.feign.VempainAdminFileClient;
import fi.poltsi.vempain.file.feign.VempainAdminFileIngestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class VempainAdminService {
	private final VempainAdminFileIngestClient vempainAdminFileIngestClient;
	private final VempainAdminFileClient vempainAdminFileClient;
	private final ObjectMapper           objectMapper;

	/**
	 * Removes a site file that an earlier {@link #uploadAsSiteFile} created; used to revert a cancelled publish.
	 */
	public void deleteSiteFile(long siteFileId) {
		log.debug("Deleting site file {} from Vempain Admin service", siteFileId);
		try {
			vempainAdminFileIngestClient.deleteSiteFile(siteFileId);
		} catch (FeignException e) {
			if (e.status() == 404) {
				log.warn("Site file {} was already gone from Vempain Admin", siteFileId);
				return;
			}
			if (e.status() == 401 || e.status() == 403) {
				throw new VempainAuthenticationException();
			}
			throw e;
		}
	}

	/**
	 * The admin users that can be granted privileges on the resources an ingest creates.
	 *
	 * @throws VempainAuthenticationException when the admin backend rejects the API token (expired, revoked or wrong network)
	 * @throws ResponseStatusException        502 when the admin backend does not answer the listing
	 */
	public List<FileIngestUserResponse> listIngestUsers() {
		log.debug("Fetching the grantable users from Vempain Admin service");
		try {
			var responseEntity = vempainAdminFileIngestClient.listIngestUsers();
			if (responseEntity == null || !responseEntity.getStatusCode()
														 .is2xxSuccessful() || responseEntity.getBody() == null) {
				log.error("User listing from Vempain admin failed with HTTP status {}", responseEntity != null ? responseEntity.getStatusCode() : "null");
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Admin backend did not answer the user listing");
			}
			return responseEntity.getBody();
		} catch (FeignException e) {
			if (e.status() == 401 || e.status() == 403) {
				log.error("The admin backend refused the API token while listing users (status {}); check vempain.service.admin-backend-api-token", e.status());
				throw new VempainAuthenticationException();
			}

			log.error("User listing failed with FeignException (status {}): {}", e.status(), e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Admin backend did not answer the user listing");
		}
	}

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
			if (e.status() == 401 || e.status() == 403) {
				log.error("The admin backend refused the API token while uploading (status {}); check vempain.service.admin-backend-api-token", e.status());
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
			var request = new SiteFilePagedRequest();
			request.setFileType(fileType);
			request.setPage(pageNumber);
			request.setSize(pageSize);
			request.setSortBy(sortBy);
			request.setDirection(direction);
			request.setSearch(filter);
			request.setFilterColumn(filterColumn);

			var responseEntity = vempainAdminFileClient.getPageableSiteFiles(request);
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
