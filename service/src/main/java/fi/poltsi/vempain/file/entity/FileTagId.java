package fi.poltsi.vempain.file.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTagId implements Serializable {

    private Long file;
    private Long tag;
}
