package fi.poltsi.vempain.file.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.json.JSONObject;

import fi.poltsi.vempain.common.metadata.GpsData;
import fi.poltsi.vempain.common.metadata.MetadataEntry;
import fi.poltsi.vempain.common.metadata.MetadataJson;
import fi.poltsi.vempain.common.metadata.RightsAndCreator;
import fi.poltsi.vempain.file.entity.FileEntity;
import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import fi.poltsi.vempain.file.entity.MetadataEntity;

/**
 * The file backend's own metadata helpers around the shared {@link MetadataJson} of vempain-common: the FFmpeg codec probe (the bundled FFmpeg is a dependency
 * of this backend only) and the mapping between the shared value types and this backend's entities. Everything ExifTool related is the shared
 * {@code ExifToolService}.
 */
public final class FileMetadataTool {

    private FileMetadataTool() {}

    /**
     * The video codec name of a container, read with the bundled FFmpeg.
     */
    public static String extractVideoCodec(File file) throws IOException {
        try (var grabber = new FFmpegFrameGrabber(file)) {
            grabber.start();
            var codecName = avcodec.avcodec_get_name(grabber.getVideoCodec());
            return codecName == null ? null : codecName.getString();
        } catch (FFmpegFrameGrabber.Exception e) {
            throw new IOException("Failed to extract video codec from " + file.getAbsolutePath(), e);
        }
    }

    /**
     * The GPS position and place names of the grouped metadata as a (detached) location entity; every field is null when the file carries none.
     */
    public static GpsLocationEntity extractGpsData(JSONObject jsonObject) {
        return toEntity(MetadataJson.extractGpsData(jsonObject));
    }

    static GpsLocationEntity toEntity(GpsData gps) {
        var entity = new GpsLocationEntity();
        entity.setLatitude(gps.getLatitude());
        entity.setLatitudeRef(gps.getLatitudeRef());
        entity.setLongitude(gps.getLongitude());
        entity.setLongitudeRef(gps.getLongitudeRef());
        entity.setAltitude(gps.getAltitude());
        entity.setDirection(gps.getDirection());
        entity.setSatelliteCount(gps.getSatelliteCount());
        entity.setCountry(gps.getCountry());
        entity.setState(gps.getState());
        entity.setCity(gps.getCity());
        entity.setStreet(gps.getStreet());
        entity.setSubLocation(gps.getSubLocation());
        return entity;
    }

    /**
     * The standard camera, rights and creator metadata of a file as the ExifTool JSON written into its export derivative, see
     * {@link MetadataJson#collectStandardMetadataAsJson}.
     */
    public static String collectStandardMetadataAsJson(List<MetadataEntity> metadataEntities, FileEntity fileEntity) {
        var entries = metadataEntities == null ? null
                : metadataEntities.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(entity -> new MetadataEntry(entity.getMetadataGroup(), entity.getMetadataKey(), entity.getMetadataValue()))
                    .toList();
        return MetadataJson.collectStandardMetadataAsJson(entries, rightsAndCreator(fileEntity));
    }

    static RightsAndCreator rightsAndCreator(FileEntity fileEntity) {
        if (fileEntity == null) {
            return null;
        }
        return new RightsAndCreator(fileEntity.getCreatorName(), fileEntity.getCreatorEmail(), fileEntity.getCreatorCountry(), fileEntity.getCreatorUrl(),
                fileEntity.getRightsHolder(), fileEntity.getRightsTerms(), fileEntity.getRightsUrl());
    }
}
