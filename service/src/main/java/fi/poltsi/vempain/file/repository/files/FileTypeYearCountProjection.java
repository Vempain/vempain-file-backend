package fi.poltsi.vempain.file.repository.files;

public interface FileTypeYearCountProjection {
	String getFileType();

	Integer getCreationYear();

	Long getFileCount();
}
