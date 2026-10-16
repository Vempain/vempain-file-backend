package fi.poltsi.vempain.file.feign;

import org.springframework.cloud.openfeign.FeignClient;

import fi.poltsi.vempain.admin.rest.file.FileIngestAPI;

@FeignClient(name = "vempain-admin-file-ingest", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminFileIngestClient extends FileIngestAPI {

}
