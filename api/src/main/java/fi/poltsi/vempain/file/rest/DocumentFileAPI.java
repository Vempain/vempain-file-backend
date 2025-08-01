package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.DocumentFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/files/document")
public interface DocumentFileAPI extends BaseRestAPI<DocumentFileResponse> {
}
