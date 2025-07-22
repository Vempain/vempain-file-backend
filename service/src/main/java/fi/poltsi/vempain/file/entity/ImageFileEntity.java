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
