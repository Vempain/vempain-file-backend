package fi.poltsi.vempain.file.tools;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Slf4j
public class FileTool {

	public static String computeSha256(File file) {
		try {
			return DigestUtils.sha256Hex(Files.readAllBytes(file.toPath()));
		} catch (IOException e) {
			return null;
		}
	}
}
