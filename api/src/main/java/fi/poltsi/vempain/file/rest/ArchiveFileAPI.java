package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ArchiveFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/files/archive")
public interface ArchiveFileAPI extends BaseRestAPI<ArchiveFileResponse> {
}
