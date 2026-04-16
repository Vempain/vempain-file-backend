package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.admin.rest.file.FileAPI;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "vempain-admin-file", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminFileClient extends FileAPI {
}

