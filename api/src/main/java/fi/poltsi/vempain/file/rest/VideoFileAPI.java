package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.VideoFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/files/video")
public interface VideoFileAPI extends BaseRestAPI<VideoFileResponse> {
}
