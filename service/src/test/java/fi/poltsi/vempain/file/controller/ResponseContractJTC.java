package fi.poltsi.vempain.file.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.file.api.response.FileStatisticsResponse;
import fi.poltsi.vempain.file.api.response.PublishFileGroupStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishFileStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishedAclResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for representative REST responses with multi-word fields: the statistics and the publication status answers.
 */
class ResponseContractJTC {

    @Test
    void fileStatisticsResponseUsesSnakeCaseNames() throws Exception {
        var response = FileStatisticsResponse.builder()
            .totalFiles(10)
            .filesByType(Map.of("IMAGE", 4L))
            .totalTags(3)
            .totalGpsLocations(2)
            .filesWithGpsLocations(2)
            .filesWithTags(3)
            .largestFileGroupSize(8)
            .smallestFileGroupSize(1)
            .averageFileGroupSize(new BigDecimal("4.50"))
            .totalFileSize(1000)
            .largestFileSize(500)
            .averageFileSize(new BigDecimal("100.00"))
            .largestFileSizeByType(Map.of("IMAGE", 500L))
            .averageFileSizeByType(Map.of("IMAGE", new BigDecimal("125.00")))
            .build();

        assertThat(new ObjectMapper().writeValueAsString(response)).isEqualTo("{\"average_file_group_size\":4.50,\"average_file_size\":100.00,"
                + "\"average_file_size_by_type\":{\"IMAGE\":125.00},\"files_by_type\":{\"IMAGE\":4},"
                + "\"files_by_type_and_year\":null,\"files_with_gps_locations\":2,\"files_with_tags\":3,"
                + "\"largest_file_group_size\":8,\"largest_file_size\":500,"
                + "\"largest_file_size_by_type\":{\"IMAGE\":500},\"smallest_file_group_size\":1,"
                + "\"total_file_size\":1000,\"total_files\":10,\"total_gps_locations\":2,\"total_tags\":3}");
    }

    @Test
    void publishStatusResponsesUseSnakeCaseNamesAndTheAclShapeOfThePublishRequest() throws Exception {
        var acl = PublishedAclResponse.builder()
            .userId(12L)
            .readPrivilege(true)
            .modifyPrivilege(true)
            .build();
        var file = PublishFileStatusResponse.builder()
            .fileId(55L)
            .published(true)
            .siteFileId(500L)
            .acls(java.util.List.of(acl))
            .build();
        var group = PublishFileGroupStatusResponse.builder()
            .fileGroupId(11L)
            .published(false)
            .siteFileIds(java.util.List.of())
            .acls(java.util.List.of())
            .build();

        // The mapper writes the properties in alphabetical order
        assertThat(new ObjectMapper().writeValueAsString(file)).isEqualTo("{\"acls\":[{\"create_privilege\":false,\"delete_privilege\":false,"
                + "\"modify_privilege\":true,\"read_privilege\":true,\"unit_id\":null,\"user_id\":12}],"
                + "\"file_id\":55,\"published\":true,\"site_file_id\":500}");
        assertThat(new ObjectMapper().writeValueAsString(group))
            .isEqualTo("{\"acls\":[],\"file_group_id\":11,\"gallery_id\":null," + "\"gallery_name\":null,\"published\":false,\"site_file_ids\":[]}");
    }
}
