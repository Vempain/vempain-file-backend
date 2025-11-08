package fi.poltsi.vempain.file.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Generic paged response wrapper")
public class PagedResponse<T> {

	@Schema(description = "List of items on the current page")
	@NotNull
	@Size(min = 0)
	private List<T> content;

	@Schema(description = "Current page index (0-based)", example = "0")
	@Min(0)
	private int page;

	@Schema(description = "Requested page size", example = "50")
	@Min(1)
	private int size;

	@Schema(description = "Total amount of items", example = "70000")
	@Min(0)
	private long totalElements;

	@Schema(description = "Total number of pages", example = "1400")
	@Min(0)
	private int totalPages;

	@Schema(description = "Is this the first page", example = "true")
	private boolean first;

	@Schema(description = "Is this the last page", example = "false")
	private boolean last;

	// Convenience factory
	public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {
		var pr = new PagedResponse<T>();
		pr.setContent(content);
		pr.setPage(page);
		pr.setSize(size);
		pr.setTotalElements(totalElements);
		pr.setTotalPages(totalPages);
		pr.setFirst(first);
		pr.setLast(last);
		return pr;
	}

	// Getters and setters
	public List<T> getContent() {
		return content;
	}

	public void setContent(List<T> content) {
		this.content = content;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public long getTotalElements() {
		return totalElements;
	}

	public void setTotalElements(long totalElements) {
		this.totalElements = totalElements;
	}

	public int getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
	}

	public boolean isFirst() {
		return first;
	}

	public void setFirst(boolean first) {
		this.first = first;
	}

	public boolean isLast() {
		return last;
	}

	public void setLast(boolean last) {
		this.last = last;
	}
}

