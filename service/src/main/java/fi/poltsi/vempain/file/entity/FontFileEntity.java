package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "font_files")
public class FontFileEntity extends FileEntity {

	@Column(name = "font_family", nullable = false)
	private String fontFamily; // Example: Arial, Roboto

	@Column
	private String weight; // Example: Bold, Regular

	@Column
	private String style; // Example: Italic, Normal
}
