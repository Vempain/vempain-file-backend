package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.auth.api.response.AbstractResponse;
import fi.poltsi.vempain.file.api.response.files.FileResponse;
import fi.poltsi.vempain.file.service.files.ImageFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test guarding the paged image lookup performance/correctness fix.
 *
 * <p>Historically {@code ImageFileService.findAll} used a query that {@code JOIN FETCH}ed the
 * {@code tags} collection while also applying {@code LIMIT}/{@code OFFSET}. Because Hibernate cannot
 * paginate a collection fetch in SQL, it loaded the whole table into memory for <em>every</em> page,
 * which made the first page take several seconds. The repository now paginates on ids at the database
 * first and only fetches the relationships for the selected page.
 *
 * <p>This test seeds a set of tagged images and verifies that pagination is correct (page content,
 * ordering, totals and deduplication despite multiple tags per image) — behaviour that must hold
 * regardless of the underlying strategy.
 */
@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImageFilePagedITC {

	private static final long   FIRST_ID    = 9001L;
	private static final int    IMAGE_COUNT = 15;
	private static final String TAG_A       = "itc-paged-tag-a";
	private static final String TAG_B       = "itc-paged-tag-b";

	@Autowired
	private ImageFileService imageFileService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long tagAId;
	private Long tagBId;

	@BeforeEach
	void seedImages() {
		cleanup();

		tagAId = jdbcTemplate.queryForObject("INSERT INTO tags (tag_name) VALUES (?) RETURNING id", Long.class, TAG_A);
		tagBId = jdbcTemplate.queryForObject("INSERT INTO tags (tag_name) VALUES (?) RETURNING id", Long.class, TAG_B);

		for (int i = 0; i < IMAGE_COUNT; i++) {
			long   id       = FIRST_ID + i;
			String filename = String.format("itc-img-%02d.jpg", i + 1);
			jdbcTemplate.update(
					"""
							INSERT INTO files
							    (id, acl_id, external_file_id, filename, file_path, mimetype,
							     filesize, sha256sum, file_type, creator, created, locked, metadata_raw)
							OVERRIDING SYSTEM VALUE
							VALUES (?, ?, ?, ?, '/itc/image', 'image/jpeg', 1024,
							        'a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0',
							        'IMAGE', 1, NOW(), false, '{}')
							""",
					id, id, "itc-ext-" + id, filename);
			jdbcTemplate.update(
					"INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 1920, 1080, 24, 72, 'itc-group')",
					id);
			// Two tags per image: exercises the de-duplication path (an image must appear only once).
			jdbcTemplate.update("INSERT INTO file_tags (file_id, tag_id) VALUES (?, ?)", id, tagAId);
			jdbcTemplate.update("INSERT INTO file_tags (file_id, tag_id) VALUES (?, ?)", id, tagBId);
		}
	}

	@AfterEach
	void cleanup() {
		jdbcTemplate.update("DELETE FROM files WHERE id BETWEEN ? AND ?", FIRST_ID, FIRST_ID + IMAGE_COUNT - 1);
		jdbcTemplate.update("DELETE FROM tags WHERE tag_name IN (?, ?)", TAG_A, TAG_B);
	}

	private PagedRequest pagedRequest(int page, int size) {
		var req = new PagedRequest();
		req.setPage(page);
		req.setSize(size);
		req.setSortBy("filename");
		req.setDirection(Sort.Direction.ASC);
		return req;
	}

	@Test
	void firstPage_returnsCorrectSlice_withRelationships_andNoDuplicates() {
		var response = imageFileService.findAll(pagedRequest(0, 10));

		assertThat(response).isNotNull();
		assertThat(response.getTotalElements()).isEqualTo(IMAGE_COUNT);
		assertThat(response.getTotalPages()).isEqualTo(2);
		assertThat(response.isFirst()).isTrue();
		assertThat(response.isLast()).isFalse();
		assertThat(response.getContent()).hasSize(10);

		// Ordered by filename ASC and de-duplicated despite each image carrying two tags.
		assertThat(response.getContent())
				.extracting(FileResponse::getFilename)
				.containsExactly(
						"itc-img-01.jpg", "itc-img-02.jpg", "itc-img-03.jpg", "itc-img-04.jpg", "itc-img-05.jpg",
						"itc-img-06.jpg", "itc-img-07.jpg", "itc-img-08.jpg", "itc-img-09.jpg", "itc-img-10.jpg");
		assertThat(response.getContent())
				.extracting(AbstractResponse::getId)
				.doesNotHaveDuplicates();

		// Relationships (tags) are eagerly loaded for the returned page.
		assertThat(response.getContent())
				.allSatisfy(r -> assertThat(r.getTags()).containsExactlyInAnyOrder(TAG_A, TAG_B));
	}

	@Test
	void secondPage_returnsRemainingSlice() {
		var response = imageFileService.findAll(pagedRequest(1, 10));

		assertThat(response).isNotNull();
		assertThat(response.getTotalElements()).isEqualTo(IMAGE_COUNT);
		assertThat(response.getTotalPages()).isEqualTo(2);
		assertThat(response.isFirst()).isFalse();
		assertThat(response.isLast()).isTrue();
		assertThat(response.getContent()).hasSize(5);
		assertThat(response.getContent())
				.extracting(FileResponse::getFilename)
				.containsExactly(
						"itc-img-11.jpg", "itc-img-12.jpg", "itc-img-13.jpg", "itc-img-14.jpg", "itc-img-15.jpg");
	}

	@Test
	void search_filtersAcrossPages() {
		var req = pagedRequest(0, 10);
		req.setSearch("itc-img-1");
		req.setCaseSensitive(Boolean.FALSE);

		var response = imageFileService.findAll(req);

		// The substring "itc-img-1" matches itc-img-10.jpg .. itc-img-15.jpg == 6 files.
		assertThat(response.getTotalElements()).isEqualTo(6);
		assertThat(response.getContent()).hasSize(6);
		assertThat(response.getContent())
				.allSatisfy(r -> assertThat(r.getFilename()).contains("itc-img-1"));
	}
}
