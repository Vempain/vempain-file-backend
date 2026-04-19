package fi.poltsi.vempain.file.feign;

import fi.poltsi.vempain.admin.rest.DataAPI;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "vempain-admin-data", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminDataClient extends DataAPI {
}
