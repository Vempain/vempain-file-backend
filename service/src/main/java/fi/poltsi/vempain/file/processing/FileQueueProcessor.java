package fi.poltsi.vempain.file.processing;

import fi.poltsi.vempain.file.api.FileTypeEnum;
import fi.poltsi.vempain.file.entity.FileProcessingQueueEntity;

public interface FileQueueProcessor {
	FileTypeEnum supportedType();

	void process(FileProcessingQueueEntity queueItem) throws Exception;
}
