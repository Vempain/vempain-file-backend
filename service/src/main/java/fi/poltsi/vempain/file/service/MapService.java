package fi.poltsi.vempain.file.service;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.poltsi.vempain.common.api.request.MapClusterFilesRequest;
import fi.poltsi.vempain.common.api.request.MapClusterRequest;
import fi.poltsi.vempain.common.api.response.MapClusterResponse;
import fi.poltsi.vempain.common.api.response.MapFileResponse;
import fi.poltsi.vempain.common.map.MapClusterQuery;
import fi.poltsi.vempain.common.map.MapQuerySchema;
import lombok.extern.slf4j.Slf4j;

/**
 * The map of the original files with GPS coordinates: the shared clustering query of {@code vempain-common-core} over this backend's {@code files} and
 * {@code gps_locations} tables, the tags of the files and their thumbnails ({@code file_thumbnails}), filtered to the files the caller may read. Location
 * guards do not apply: the map is the file administrator's own view of the originals, the guards only decide what is published.
 */
@Slf4j
@Service
public class MapService {

    /** Where this backend keeps its GPS-tagged files, for the shared query */
    static final MapQuerySchema SCHEMA = MapQuerySchema.builder()
        .fileTable("files")
        .idColumn("id")
        .aclIdColumn("acl_id")
        .filenameColumn("filename")
        .filePathColumn("file_path")
        .fileTypeColumn("file_type")
        .mimetypeColumn("mimetype")
        .datetimeColumn("original_datetime")
        .locationIdColumn("gps_location_id")
        .locationTable("gps_locations")
        .tagLinkTable("file_tags")
        .tagLinkFileColumn("file_id")
        .tagLinkTagColumn("tag_id")
        .tagTable("tags")
        .tagNameColumn("tag_name")
        // the thumbnail is a sub-resource of the parent file; the file SPA shows it through the thumbnail content endpoint
        .thumbnailSubquery("(SELECT MIN(t.id) FROM file_thumbnails t WHERE t.file_id = p.id)")
        .build();

    private final MapClusterQuery query;
    private final FileAclService fileAclService;

    public MapService(DataSource dataSource, FileAclService fileAclService) {
        this.query = new MapClusterQuery(new NamedParameterJdbcTemplate(dataSource), SCHEMA);
        this.fileAclService = fileAclService;
    }

    @Transactional(readOnly = true)
    public MapClusterResponse clusters(MapClusterRequest request) {
        var response = query.clusters(request, fileAclService.mapPrincipal());
        log.debug("Map clusters at zoom {} (precision {}): {} clusters, {} files", request.getZoom(), response.getPrecision(), response.getClusters()
            .size(), response.getTotalPoints());
        return response;
    }

    @Transactional(readOnly = true)
    public List<MapFileResponse> files(MapClusterFilesRequest request) {
        return query.files(request, fileAclService.mapPrincipal());
    }
}
