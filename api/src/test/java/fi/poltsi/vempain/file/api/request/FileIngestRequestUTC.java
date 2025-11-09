package fi.poltsi.vempain.file.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIngestRequestUTC {

	private FileIngestRequest sample() {
		return FileIngestRequest.builder()
								.fileName("img_001.jpg")
								.filePath("gallery/2025/08")
								.mimeType("image/jpeg")
								.comment("Some comment")
								.metadata("{\"some-field\":\"some value\"}")
								.sha256sum("abc123")
								.galleryId(1001L)
								.galleryName("Summer 2025")
								.galleryDescription("A sunny album from August 2025")
								.build();
	}

	@Test
	void setFileName() {
		FileIngestRequest r = sample();
		r.setFileName("new.jpg");
		assertEquals("new.jpg", r.getFileName());
	}

	@Test
	void setFilePath() {
		FileIngestRequest r = sample();
		r.setFilePath("other/path");
		assertEquals("other/path", r.getFilePath());
	}

	@Test
	void setMimeType() {
		FileIngestRequest r = sample();
		r.setMimeType("image/png");
		assertEquals("image/png", r.getMimeType());
	}

	@Test
	void setComment() {
		FileIngestRequest r = sample();
		r.setComment("Updated");
		assertEquals("Updated", r.getComment());
	}

	@Test
	void setMetadata() {
		FileIngestRequest r = sample();
		r.setMetadata("{}");
		assertEquals("{}", r.getMetadata());
	}

	@Test
	void setSha256sum() {
		FileIngestRequest r = sample();
		r.setSha256sum("def456");
		assertEquals("def456", r.getSha256sum());
	}

	@Test
	void setGalleryId() {
		FileIngestRequest r = sample();
		r.setGalleryId(222L);
		assertEquals(222L, r.getGalleryId());
	}

	@Test
	void setGalleryName() {
		FileIngestRequest r = sample();
		r.setGalleryName("New Name");
		assertEquals("New Name", r.getGalleryName());
	}

	@Test
	void setGalleryDescription() {
		FileIngestRequest r = sample();
		r.setGalleryDescription("New Desc");
		assertEquals("New Desc", r.getGalleryDescription());
	}

	@Test
	void testEquals() {
		FileIngestRequest a = sample();
		FileIngestRequest b = sample();
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		b.setFileName("different.jpg");
		assertNotEquals(a, b);
	}

	@Test
	void canEqual() {
		FileIngestRequest a = sample();
		assertTrue(a.canEqual(sample()));
		// Different class
		assertFalse(a.canEqual("not a request"));
	}

	@Test
	void testHashCode() {
		FileIngestRequest a = sample();
		FileIngestRequest b = sample();
		assertEquals(a.hashCode(), b.hashCode());
		b.setSha256sum("changed");
		assertNotEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void testToString() {
		String s = sample().toString();
		assertTrue(s.contains("FileIngestRequest"));
		assertTrue(s.contains("img_001.jpg"));
		assertTrue(s.contains("image/jpeg"));
	}

	@Test
	void builder() {
		FileIngestRequest r = FileIngestRequest.builder()
											   .fileName("f.txt")
											   .filePath("x/y")
											   .mimeType("text/plain")
											   .comment("c")
											   .metadata("{}")
											   .sha256sum("sum")
											   .galleryId(2L)
											   .galleryName("G")
											   .galleryDescription("Desc")
											   .build();
		assertAll(
				() -> assertEquals("f.txt", r.getFileName()),
				() -> assertEquals("x/y", r.getFilePath()),
				() -> assertEquals("text/plain", r.getMimeType()),
				() -> assertEquals("c", r.getComment()),
				() -> assertEquals("{}", r.getMetadata()),
				() -> assertEquals("sum", r.getSha256sum()),
				() -> assertEquals(2L, r.getGalleryId()),
				() -> assertEquals("G", r.getGalleryName()),
				() -> assertEquals("Desc", r.getGalleryDescription())
		);
	}

	@Test
	void jsonDeserialization() throws Exception {
		String json = """
				{
				  "file_name": "img_001.png",
				  "file_path": "test-gallery",
				  "mime_type": "image/png",
				  "comment": "Some comment",
				  "metadata": "{\\"some_field\\":\\"some value\\"}",
				  "sha256sum": "3f786850e387550fdab836ed7e6dc881de23001b",
				  "gallery_id": 1,
				  "gallery_name": "Summer 2025",
				  "gallery_description": "A sunny album from August 2025"
				}
				""";
		ObjectMapper      mapper = new ObjectMapper();
		FileIngestRequest r      = mapper.readValue(json, FileIngestRequest.class);
		assertAll(
				() -> assertEquals("img_001.png", r.getFileName()),
				() -> assertEquals("test-gallery", r.getFilePath()),
				() -> assertEquals("image/png", r.getMimeType()),
				() -> assertEquals("Some comment", r.getComment()),
				() -> assertEquals("{\"some_field\":\"some value\"}", r.getMetadata()),
				() -> assertEquals("3f786850e387550fdab836ed7e6dc881de23001b", r.getSha256sum()),
				() -> assertEquals(1L, r.getGalleryId()),
				() -> assertEquals("Summer 2025", r.getGalleryName()),
				() -> assertEquals("A sunny album from August 2025", r.getGalleryDescription())
		);
	}
}
