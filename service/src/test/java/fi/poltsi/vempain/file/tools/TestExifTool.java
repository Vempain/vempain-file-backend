package fi.poltsi.vempain.file.tools;

import fi.poltsi.vempain.common.metadata.ExifToolProperties;
import fi.poltsi.vempain.common.metadata.ExifToolService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * One real {@link ExifToolService} (the exiftool on the PATH, one persistent process) for tests that prepare or inspect files with ExifTool.
 */
public final class TestExifTool {

    private static final ExifToolService SERVICE = new ExifToolService(properties(), new SimpleMeterRegistry());

    private TestExifTool() {}

    private static ExifToolProperties properties() {
        var properties = new ExifToolProperties();
        properties.setPoolSize(1);
        return properties;
    }

    public static ExifToolService service() {
        return SERVICE;
    }
}
