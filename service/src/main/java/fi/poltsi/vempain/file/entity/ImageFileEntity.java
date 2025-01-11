package fi.poltsi.vempain.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table(name = "image_files")
public class ImageFileEntity extends FileEntity {

	@Column(name="width", nullable = false)
	private int width;

	@Column(name="height", nullable = false)
	private int height;

	@Column(name = "color_depth")
	private int colorDepth; // Optional: Color depth in bits

	@Column
	private int dpi; // Optional: Dots per inch
}
