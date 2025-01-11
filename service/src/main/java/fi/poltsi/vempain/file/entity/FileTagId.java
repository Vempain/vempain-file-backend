package fi.poltsi.vempain.file.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTagId implements Serializable {
	private Long file;
	private Long tag;
}
