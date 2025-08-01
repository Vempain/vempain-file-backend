package fi.poltsi.vempain.file.rest;

import fi.poltsi.vempain.file.api.response.VectorFileResponse;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/files/vector")
public interface VectorFileAPI extends BaseRestAPI<VectorFileResponse> {
}
