package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.IconFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/files/icon")
public interface IconFileAPI extends BaseRestAPI<IconFileResponse> {
}
