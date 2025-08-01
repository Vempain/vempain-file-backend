package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.AudioFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/files/audio")
public interface AudioFileAPI extends BaseRestAPI<AudioFileResponse> {
}
