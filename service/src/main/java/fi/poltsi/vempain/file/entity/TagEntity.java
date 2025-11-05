package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.request.TagRequest;
import fi.poltsi.vempain.file.api.response.TagResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tag_name", nullable = false, unique = true, length = 128)
	private String tagName;

	@Column(name = "tag_name_de", length = 128)
	private String tagNameDe;

	@Column(name = "tag_name_en", length = 128)
	private String tagNameEn;

	@Column(name = "tag_name_es", length = 128)
	private String tagNameEs;

	@Column(name = "tag_name_fi", length = 128)
	private String tagNameFi;

	@Column(name = "tag_name_sv", length = 128)
	private String tagNameSv;

	@ManyToMany(mappedBy = "tags")
	@Builder.Default
	private Set<FileEntity> files = new HashSet<>();

	/**
	 * Maps this entity to TagResponse.
	 */
	public TagResponse toResponse() {
		return TagResponse.builder()
						  .id(getId())
						  .tagName(getTagName())
						  .tagNameDe(getTagNameDe())
						  .tagNameEn(getTagNameEn())
						  .tagNameEs(getTagNameEs())
						  .tagNameFi(getTagNameFi())
						  .tagNameSv(getTagNameSv())
						  .build();
	}

	/**
	 * Maps this entity to TagRequest.
	 */
	public TagRequest toRequest() {
		return new TagRequest(
				getId(),
				getTagName(),
				getTagNameDe(),
				getTagNameEn(),
				getTagNameEs(),
				getTagNameFi(),
				getTagNameSv()
		);
	}
}
