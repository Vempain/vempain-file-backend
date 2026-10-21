package fi.poltsi.vempain.file.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.common.api.response.TaskAcceptedResponse;
import fi.poltsi.vempain.file.api.request.PublishAllFileGroupsRequest;
import fi.poltsi.vempain.file.api.request.PublishFileGroupRequest;
import fi.poltsi.vempain.file.api.request.PublishFileRequest;
import fi.poltsi.vempain.file.api.request.PublishFilesRequest;
import fi.poltsi.vempain.file.api.response.PublishFileGroupStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishFileStatusResponse;
import fi.poltsi.vempain.file.api.response.PublishUnitResponse;
import fi.poltsi.vempain.file.api.response.PublishUserResponse;
import fi.poltsi.vempain.file.rest.PublishAPI;
import fi.poltsi.vempain.file.service.PublishService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PublishController implements PublishAPI {

    private final PublishService publishService;

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishFile(PublishFileRequest request) {
        // 404 when the file does not exist, 403 when the caller may not modify it; both are decided before the task starts
        publishService.authorizeFilePublish(request.getFileId());
        return ResponseEntity.accepted()
            .body(publishService.publishFile(request)
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishFiles(PublishFilesRequest request) {
        publishService.authorizeFilesPublish(request.getFileIds());
        return ResponseEntity.accepted()
            .body(publishService.publishFiles(request)
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishFileGroup(PublishFileGroupRequest request) {
        if (!publishService.fileGroupExists(request.getFileGroupId())) {
            return ResponseEntity.notFound()
                .build();
        }

        publishService.authorizeFileGroupPublish(request.getFileGroupId());
        return ResponseEntity.accepted()
            .body(publishService.publishFileGroup(request)
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<TaskAcceptedResponse> publishAllFileGroups(PublishAllFileGroupsRequest request) {
        return ResponseEntity.accepted()
            .body(publishService.publishAllFileGroups(request)
                .toAcceptedResponse());
    }

    @Override
    public ResponseEntity<List<PublishUserResponse>> listPublishUsers() {
        return ResponseEntity.ok(publishService.listPublishUsers());
    }

    @Override
    public ResponseEntity<List<PublishUnitResponse>> listPublishUnits() {
        return ResponseEntity.ok(publishService.listPublishUnits());
    }

    @Override
    public ResponseEntity<PublishFileStatusResponse> getPublishFileStatus(long fileId) {
        // 404 for an unknown file and 403 without the modify privilege, like the publish itself
        return ResponseEntity.ok(publishService.publishFileStatus(fileId));
    }

    @Override
    public ResponseEntity<PublishFileGroupStatusResponse> getPublishFileGroupStatus(long fileGroupId) {
        return ResponseEntity.ok(publishService.publishFileGroupStatus(fileGroupId));
    }
}
