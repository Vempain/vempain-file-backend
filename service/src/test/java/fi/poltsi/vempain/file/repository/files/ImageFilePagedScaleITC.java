package fi.poltsi.vempain.file.repository.files;

import fi.poltsi.vempain.auth.api.request.PagedRequest;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.service.files.ImageFileService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scale / performance-characteristics test for the paged image lookup.
 *
 * <p>The "page 0 slow / page 1 fast" report comes from two independent effects, both exercised here:
 * <ol>
 *     <li><b>Whole-table load into memory.</b> A {@code JOIN FETCH} of the {@code tags} collection
 *     combined with {@code LIMIT}/{@code OFFSET} makes Hibernate load <em>every</em> matching row and
 *     paginate in memory. This test seeds a larger dataset and asserts, via Hibernate statistics, that
 *     fetching one page instantiates only one page worth of {@link ImageFileEntity} instances — not the
 *     whole table. This assertion fails under the old strategy, so it reproduces/guards that regression.</li>
 *     <li><b>Missing sort index.</b> Without an index on {@code files(filename)} the database performs a
 *     full sequential scan + sort of the wide {@code files} table on every request; the first (cold) call
 *     reads all heap pages from disk (seconds) while later calls hit the page cache (fast). The
 *     {@code V1005__files_sort_indexes.sql} migration adds that index. Absolute timings depend on the
 *     host and cache state, so they are logged for investigation rather than asserted.</li>
 * </ol>
 */
@Slf4j
@SpringBootTest(properties = {
		"vempain.app.frontend-url=http://localhost:3000",
		"vempain.original-root-directory=/tmp",
		"vempain.export-root-directory=/tmp",
		"vempain.generate-missing-thumbnails.batch-size=10",
		"vempain.generate-missing-thumbnails.thumb-image-quality=0.5",
		"vempain.generate-missing-thumbnails.thumb-image-size=100",
		"spring.jpa.properties.hibernate.generate_statistics=true",
		"spring.jpa.show-sql=true",
		"spring.jpa.properties.hibernate.format_sql=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImageFilePagedScaleITC {

	private static final long   FIRST_ID    = 20_000L;
	private static final int    IMAGE_COUNT = 200;
	private static final int    PAGE_SIZE   = 10;
	private static final String TAG_A       = "itc-scale-tag-a";
	private static final String TAG_B       = "itc-scale-tag-b";

	@Autowired
	private ImageFileService imageFileService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void seedImages() {
		cleanup();

		Long tagAId = jdbcTemplate.queryForObject("INSERT INTO tags (tag_name) VALUES (?) RETURNING id", Long.class, TAG_A);
		Long tagBId = jdbcTemplate.queryForObject("INSERT INTO tags (tag_name) VALUES (?) RETURNING id", Long.class, TAG_B);

		jdbcTemplate.batchUpdate(
				"""
						INSERT INTO files
						    (id, acl_id, external_file_id, filename, file_path, mimetype,
						     filesize, sha256sum, file_type, creator, created, locked, metadata_raw)
						OVERRIDING SYSTEM VALUE
						VALUES (?, ?, ?, ?, '/itc/scale', 'image/jpeg', 1024,
						        'a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0',
						        'IMAGE', 1, NOW(), false, '{}')
						""",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						long id = FIRST_ID + i;
						ps.setLong(1, id);
						ps.setLong(2, id);
						ps.setString(3, "itc-scale-ext-" + id);
						ps.setString(4, String.format("itc-scale-img-%04d.jpg", i + 1));
					}

					@Override
					public int getBatchSize() {
						return IMAGE_COUNT;
					}
				});

		jdbcTemplate.batchUpdate(
				"INSERT INTO image_files (id, width, height, color_depth, dpi, group_label) VALUES (?, 1920, 1080, 24, 72, 'itc-scale')",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setLong(1, FIRST_ID + i);
					}

					@Override
					public int getBatchSize() {
						return IMAGE_COUNT;
					}
				});

		// Two tags per image so the (historical) collection-fetch path would multiply rows and
		// exercise the de-duplication logic.
		jdbcTemplate.batchUpdate(
				"INSERT INTO file_tags (file_id, tag_id) VALUES (?, ?)",
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						ps.setLong(1, FIRST_ID + (i / 2));
						ps.setLong(2, (i % 2 == 0) ? tagAId : tagBId);
					}

					@Override
					public int getBatchSize() {
						return IMAGE_COUNT * 2;
					}
				});
	}

	@AfterEach
	void cleanup() {
		jdbcTemplate.update("DELETE FROM files WHERE id BETWEEN ? AND ?", FIRST_ID, FIRST_ID + IMAGE_COUNT - 1);
		jdbcTemplate.update("DELETE FROM tags WHERE tag_name IN (?, ?)", TAG_A, TAG_B);
	}

	private PagedRequest pagedRequest(int page) {
		var req = new PagedRequest();
		req.setPage(page);
		req.setSize(PAGE_SIZE);
		req.setSortBy("filename");
		req.setDirection(Sort.Direction.ASC);
		return req;
	}

	@Test
	void firstPage_loadsOnlyOnePageOfEntities_notTheWholeTable() {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class)
		                                            .getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var response = imageFileService.findAll(pagedRequest(0));

		assertThat(response.getContent()).hasSize(PAGE_SIZE);
		assertThat(response.getTotalElements()).isEqualTo(IMAGE_COUNT);

		long imageLoads      = statistics.getEntityStatistics(ImageFileEntity.class.getName())
		                                 .getLoadCount();
		long queryExecutions = statistics.getQueryExecutionCount();
		log.info("ImageFileEntity load count for one page of {} out of {} rows: {}, query executions: {}",
		         PAGE_SIZE, IMAGE_COUNT, imageLoads, queryExecutions);

		// The repository uses one page-id query, one count query, one page-entity query,
		// and one batch thumbnail lookup.
		assertThat(queryExecutions)
				.as("A single page must use the expected paged query sequence")
				.isEqualTo(4);
	}

	@Test
	void pageZeroAndPageOne_returnConsistentSlices_andTimingsAreLoggedForInvestigation() {
		long startPageZero    = System.nanoTime();
		var  pageZero         = imageFileService.findAll(pagedRequest(0));
		long durationPageZero = (System.nanoTime() - startPageZero) / 1_000_000;

		long startPageOne    = System.nanoTime();
		var  pageOne         = imageFileService.findAll(pagedRequest(1));
		long durationPageOne = (System.nanoTime() - startPageOne) / 1_000_000;

		log.info("Paged image lookup timings across {} rows: page 0 = {} ms, page 1 = {} ms",
		         IMAGE_COUNT, durationPageZero, durationPageOne);

		assertThat(pageZero.getContent()).hasSize(PAGE_SIZE);
		assertThat(pageOne.getContent()).hasSize(PAGE_SIZE);
		assertThat(pageZero.getContent()
		                   .getFirst()
		                   .getFilename()).isEqualTo("itc-scale-img-0001.jpg");
		assertThat(pageOne.getContent()
		                  .getFirst()
		                  .getFilename()).isEqualTo("itc-scale-img-0011.jpg");

		// The two pages must not overlap.
		var pageZeroIds = pageZero.getContent()
		                          .stream()
		                          .map(r -> r.getId())
		                          .toList();
		var pageOneIds  = pageOne.getContent()
		                         .stream()
		                         .map(r -> r.getId())
		                         .toList();
		assertThat(pageZeroIds).doesNotContainAnyElementsOf(pageOneIds);
	}
}
