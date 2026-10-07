package fi.poltsi.vempain.file.service;

import fi.poltsi.vempain.file.entity.ExportFileEntity;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.FileGroupEntity;

/**
 * Callbacks the directory scan reports to its caller: a cancellation checkpoint before every file and the entities the scan
 * created, so that a cancelled scan task can remove them again. Entities that already existed and were refreshed are not reported;
 * their previous state is gone and cannot be restored.
 */
public interface ScanRecorder {
	ScanRecorder NONE = new ScanRecorder() {
	};

	/**
	 * Called before each file; may throw to stop the scan.
	 */
	default void checkpoint() {
	}

	default void fileGroupCreated(FileGroupEntity fileGroup) {
	}

	default void fileCreated(FileEntity file) {
	}

	default void exportFileCreated(ExportFileEntity exportFile) {
	}
}
