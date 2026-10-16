package fi.poltsi.vempain.file.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.codec.digest.DigestUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileTool {

    public static String computeSha256(File file) {
        try {
            return DigestUtils.sha256Hex(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            log.warn("Failed to generate sha256sum for file {}", file, e);
            return null;
        }
    }
}
