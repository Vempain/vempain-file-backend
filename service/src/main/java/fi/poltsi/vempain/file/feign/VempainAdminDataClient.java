package fi.poltsi.vempain.file.feign;

import org.springframework.cloud.openfeign.FeignClient;

import fi.poltsi.vempain.admin.rest.DataAPI;

@FeignClient(name = "vempain-admin-data", url = "${vempain.service.admin-backend-url}")
public interface VempainAdminDataClient extends DataAPI {
}
