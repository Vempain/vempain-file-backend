package fi.poltsi.vempain.file.processing;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import fi.poltsi.vempain.common.api.FileTypeEnum;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FileQueueProcessorFactory {

    private final List<FileQueueProcessor> processors;

    public FileQueueProcessor processorFor(FileTypeEnum fileType) {
        Map<FileTypeEnum, FileQueueProcessor> processorsByType = new EnumMap<>(FileTypeEnum.class);
        processors.forEach(processor -> processorsByType.put(processor.supportedType(), processor));
        var processor = processorsByType.get(fileType);
        if (processor == null) {
            throw new IllegalArgumentException("No queue processor registered for file type " + fileType);
        }
        return processor;
    }
}
