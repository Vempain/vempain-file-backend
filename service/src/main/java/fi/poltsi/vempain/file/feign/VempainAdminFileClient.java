package fi.poltsi.vempain.file.feign;

import org.springframework.cloud.openfeign.FeignClient;

import fi.poltsi.vempain.admin.rest.file.FileAPI;

@FeignClient(name = "vempain-admin-file", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminFileClient extends FileAPI {
}
