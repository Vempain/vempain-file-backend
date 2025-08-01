package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.ImageFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/files/image")
public interface ImageFileAPI extends BaseRestAPI<ImageFileResponse> {
}
