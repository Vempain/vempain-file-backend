package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.auth.rest.LoginAPI;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "vempain-admin-login", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminLoginClient extends LoginAPI {
}
