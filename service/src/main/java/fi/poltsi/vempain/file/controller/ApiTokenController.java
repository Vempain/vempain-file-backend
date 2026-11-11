package fi.poltsi.vempain.file.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import fi.poltsi.vempain.auth.security.AdministrationGuard;
import fi.poltsi.vempain.auth.tools.AuthTools;
import fi.poltsi.vempain.common.api.request.ApiTokenRequest;
import fi.poltsi.vempain.common.api.response.ApiTokenCreatedResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenNetworkResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenResponse;
import fi.poltsi.vempain.file.rest.ApiTokenAPI;
import fi.poltsi.vempain.file.service.ApiTokenService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ApiTokenController implements ApiTokenAPI {

    private final ApiTokenService apiTokenService;
    private final AdministrationGuard administrationGuard;

    @Override
    public ResponseEntity<List<ApiTokenResponse>> getApiTokens() {
        administrationGuard.requireAdministrator();
        return ResponseEntity.ok(apiTokenService.findAll());
    }

    @Override
    public ResponseEntity<ApiTokenNetworkResponse> getDefaultNetwork() {
        administrationGuard.requireAdministrator();
        return ResponseEntity.ok(apiTokenService.defaultNetwork());
    }

    @Override
    public ResponseEntity<ApiTokenCreatedResponse> createApiToken(ApiTokenRequest request) {
        administrationGuard.requireAdministrator();
        return ResponseEntity.ok(apiTokenService.create(request, AuthTools.getCurrentUserId()));
    }

    @Override
    public ResponseEntity<Void> deleteApiToken(long tokenId) {
        administrationGuard.requireAdministrator();
        apiTokenService.delete(tokenId);
        return ResponseEntity.noContent()
            .build();
    }
}
