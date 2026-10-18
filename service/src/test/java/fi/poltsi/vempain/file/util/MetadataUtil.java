package fi.poltsi.vempain.file.util;

import java.util.List;

import fi.poltsi.vempain.file.entity.MetadataEntity;

public class MetadataUtil {

    public static List<MetadataEntity> generateMetadataEntities() {
        var me1 = MetadataEntity.builder()
            .metadataGroup("IFD0")
            .metadataKey("Artist")
            .metadataValue("John Smith")
            .build();
        var me2 = MetadataEntity.builder()
            .metadataGroup("ExifIFD")
            .metadataKey("FNumber")
            .metadataValue("2.8")
            .build();
        var me3 = MetadataEntity.builder()
            .metadataGroup("ExifIFD")
            .metadataKey("ExposureTime")
            .metadataValue("1/200")
            .build();
        var me4 = MetadataEntity.builder()
            .metadataGroup("IFD0")
            .metadataKey("Make")
            .metadataValue("Canon")
            .build();
        var isoMe = MetadataEntity.builder()
            .metadataGroup("ExifIFD")
            .metadataKey("ISO")
            .metadataValue("400")
            .build();
        var bpsMe = MetadataEntity.builder()
            .metadataGroup("File")
            .metadataKey("BitsPerSample")
            .metadataValue("8")
            .build();
        var megapixelsMe = MetadataEntity.builder()
            .metadataGroup("Composite")
            .metadataKey("Megapixels")
            .metadataValue("24.2")
            .build();
        var widthMe = MetadataEntity.builder()
            .metadataGroup("File")
            .metadataKey("ImageWidth")
            .metadataValue("1920")
            .build();
        var heightMe = MetadataEntity.builder()
            .metadataGroup("File")
            .metadataKey("ImageHeight")
            .metadataValue("1080")
            .build();
        return List.of(me1, me2, me3, me4, isoMe, bpsMe, megapixelsMe, widthMe, heightMe);
    }
}
