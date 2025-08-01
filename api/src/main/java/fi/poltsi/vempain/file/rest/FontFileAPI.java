package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.FontFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/files/font")
public interface FontFileAPI extends BaseRestAPI<FontFileResponse> {
}
