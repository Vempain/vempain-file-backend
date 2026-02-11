package fi.poltsi.vempain.file.entity;

import fi.poltsi.vempain.file.api.response.files.ExecutableFileResponse;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "executable_files")
public class ExecutableFileEntity extends FileEntity {

	// Example values: WINDOWS, LINUX, MACOS, ANDROID, JVM, OTHER
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "executable_os", joinColumns = @JoinColumn(name = "file_id"))
	@Column(name = "os", nullable = false)
	@lombok.Builder.Default
	private Set<String> operatingSystems = new HashSet<>();

	// true if script (sh, bat, ps1, py ...), false if native/binary
	@Column(name = "is_script", nullable = false)
	private boolean script;

	@Override
	public ExecutableFileResponse toResponse() {
		ExecutableFileResponse response = new ExecutableFileResponse();
		populateBaseResponse(response);
		response.setOperatingSystems(getOperatingSystems() == null ? null : new java.util.HashSet<>(getOperatingSystems()));
		response.setScript(isScript());
		return response;
	}
}
