package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.admin.rest.file.FileIngestAPI;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "vempain-admin-file-ingest", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminFileIngestClient extends FileIngestAPI {

}
