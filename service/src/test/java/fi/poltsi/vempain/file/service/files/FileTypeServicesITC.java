package fi.poltsi.vempain.file.service.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FileTypeServicesITC {

	@Autowired
	private ArchiveFileService     archiveFileService;
	@Autowired
	private AudioFileService       audioFileService;
	@Autowired
	private BinaryFileService      binaryFileService;
	@Autowired
	private DataFileService        dataFileService;
	@Autowired
	private DocumentFileService    documentFileService;
	@Autowired
	private ExecutableFileService  executableFileService;
	@Autowired
	private FontFileService        fontFileService;
	@Autowired
	private IconFileService        iconFileService;
	@Autowired
	private ImageFileService       imageFileService;
	@Autowired
	private InteractiveFileService interactiveFileService;
	@Autowired
	private ThumbFileService       thumbFileService;
	@Autowired
	private VectorFileService      vectorFileService;
	@Autowired
	private VideoFileService       videoFileService;

	@Test
	void findAll_worksAcrossAllFileTypeServices() {
		var req = new PagedRequest();
		req.setPage(0);
		req.setSize(10);
		req.setSearch("sample");
		req.setCaseSensitive(Boolean.FALSE);
		req.setSortBy("filename");
		req.setDirection(org.springframework.data.domain.Sort.Direction.ASC);

		assertThat(archiveFileService.findAll(req)).isNotNull();
		assertThat(audioFileService.findAll(req)).isNotNull();
		assertThat(binaryFileService.findAll(req)).isNotNull();
		assertThat(dataFileService.findAll(req)).isNotNull();
		assertThat(documentFileService.findAll(req)).isNotNull();
		assertThat(executableFileService.findAll(req)).isNotNull();
		assertThat(fontFileService.findAll(req)).isNotNull();
		assertThat(iconFileService.findAll(req)).isNotNull();
		assertThat(imageFileService.findAll(req)).isNotNull();
		assertThat(interactiveFileService.findAll(req)).isNotNull();
		assertThat(thumbFileService.findAll(req)).isNotNull();
		assertThat(vectorFileService.findAll(req)).isNotNull();
		assertThat(videoFileService.findAll(req)).isNotNull();
	}
}
