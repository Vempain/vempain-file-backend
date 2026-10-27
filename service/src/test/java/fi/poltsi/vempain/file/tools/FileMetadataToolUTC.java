package fi.poltsi.vempain.file.tools;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.ImageFileEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The file backend's adapter around the shared metadata parsers: GPS values become a location entity, metadata rows and the file's rights become the export
 * JSON, and an unreadable video is an {@link IOException}.
 */
class FileMetadataToolUTC {

    @Test
    void gpsValuesBecomeALocationEntity() {
        var metadata = new JSONObject().put("Composite", new JSONObject().put("GPSLocation", "60 deg 10' 12.00\" N, 24 deg 56' 24.00\" E")
            .put("GPSAltitude", "7 m Above Sea Level"))
            .put("GPS", new JSONObject().put("GPSSatellites", "07")
                .put("GPSImgDirection", 212.1))
            .put("XMP-iptcCore", new JSONObject().put("Country", "FI")
                .put("Location", "Mannerheimintie"))
            .put("XMP-photoshop", new JSONObject().put("State", "Uusimaa")
                .put("City", "Helsinki"));

        var entity = FileMetadataTool.extractGpsData(metadata);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getLatitudeRef()).isEqualTo('N');
        assertThat(entity.getLatitude()).isEqualByComparingTo(new BigDecimal("60.17000"));
        assertThat(entity.getLongitudeRef()).isEqualTo('E');
        assertThat(entity.getLongitude()).isEqualByComparingTo(new BigDecimal("24.94000"));
        assertThat(entity.getAltitude()).isEqualTo(7.0);
        assertThat(entity.getSatelliteCount()).isEqualTo(7);
        assertThat(entity.getDirection()).isEqualTo(212.1);
        assertThat(entity.getCountry()).isEqualTo("FI");
        assertThat(entity.getState()).isEqualTo("Uusimaa");
        assertThat(entity.getCity()).isEqualTo("Helsinki");
        assertThat(entity.getStreet()).isEqualTo("Mannerheimintie");

        var empty = FileMetadataTool.extractGpsData(new JSONObject());
        assertThat(empty.getLatitude()).isNull();
        assertThat(empty.getCountry()).isNull();
    }

    @Test
    void metadataRowsAndFileRightsBecomeTheExportJson() throws Exception {
        FileEntity file = new ImageFileEntity();
        file.setCreatorName("Entity Artist");
        file.setCreatorEmail("e@x");
        file.setCreatorCountry("FI");
        file.setCreatorUrl("http://creator");
        file.setRightsHolder("Entity Rights");
        file.setRightsTerms(" terms ");
        file.setRightsUrl("http://rights");
        var rows = List.of(MetadataEntity.builder()
            .metadataGroup("IFD0")
            .metadataKey("Make")
            .metadataValue("Canon")
            .build(),
                MetadataEntity.builder()
                    .metadataGroup("ExifIFD")
                    .metadataKey("ISO")
                    .metadataValue("400")
                    .build());

        var node = JsonMapper.shared()
            .readTree(FileMetadataTool.collectStandardMetadataAsJson(rows, file))
            .get(0);

        assertThat(node.get("Make")
            .asString()).isEqualTo("Canon");
        assertThat(node.get("ISO")
            .asInt()).isEqualTo(400);
        assertThat(node.get("Artist")
            .asString()).isEqualTo("Entity Artist");
        assertThat(node.get("Copyright")
            .asString()).isEqualTo("Entity Rights");
        assertThat(node.get("UsageTerms")
            .asString()).isEqualTo("terms");
        assertThat(node.get("WebStatement")
            .asString()).isEqualTo("http://rights");
        assertThat(node.get("CreatorWorkEmail")
            .asString()).isEqualTo("e@x");
        assertThat(node.get("CreatorCountry")
            .asString()).isEqualTo("FI");
        assertThat(node.get("CreatorWorkURL")
            .asString()).isEqualTo("http://creator");

        assertThat(FileMetadataTool.collectStandardMetadataAsJson(null, null)).isEqualTo("[{}]");
        assertThat(FileMetadataTool.rightsAndCreator(null)).isNull();
    }

    @Test
    void reportsUnreadableVideoAsIoException(@TempDir Path tempDirectory) {
        var exception = assertThrows(IOException.class, () -> FileMetadataTool.extractVideoCodec(tempDirectory.resolve("missing.mp4")
            .toFile()));

        assertThat(exception.getMessage()).contains("missing.mp4");
    }
}
