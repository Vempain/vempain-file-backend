package fi.poltsi.vempain.file.rest;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import fi.poltsi.vempain.common.api.request.ApiTokenRequest;
import fi.poltsi.vempain.common.api.response.ApiTokenCreatedResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenNetworkResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Management of the read-only API tokens other services (vempain-mcp) present in the {@link #API_TOKEN_HEADER} header instead of logging in. Administrator ACL
 * required; the token endpoints themselves can never be called with a token.
 */
@Tag(name = "ApiTokenAPI", description = "Read-only API tokens")
public interface ApiTokenAPI {

    String BASE_PATH = "/api-tokens";

    /**
     * Header carrying an API token, the same name as the admin backend uses.
     */
    String API_TOKEN_HEADER = "X-Vempain-Api-Token";

    @Operation(summary = "List the API tokens", description = "Every token without its secret, newest first", tags = "ApiTokenAPI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of tokens",
                content = {
                    @Content(array = @ArraySchema(schema = @Schema(implementation = ApiTokenResponse.class)), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "Administrator access is required", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(value = BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<ApiTokenResponse>> getApiTokens();

    @Operation(summary = "Suggested network for a new token",
            description = "The private network the file backend shares with the calling services (detected from its own interfaces when "
                    + "vempain.file.api-token.private-network is true, or vempain.file.api-token.default-network), plus every private network "
                    + "it is attached to. Source NONE means the administrator has to enter the network by hand.",
            tags = "ApiTokenAPI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Suggestion",
                content = { @Content(schema = @Schema(implementation = ApiTokenNetworkResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "Administrator access is required", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(value = BASE_PATH + "/default-network", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiTokenNetworkResponse> getDefaultNetwork();

    @Operation(summary = "Create an API token",
            description = "Generates a random MCP_READ token bound to the creating administrator, the given network and expiry. The token string is "
                    + "returned once and only its hash is stored.",
            tags = "ApiTokenAPI")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token created",
                content = { @Content(schema = @Schema(implementation = ApiTokenCreatedResponse.class), mediaType = MediaType.APPLICATION_JSON_VALUE) }),
        @ApiResponse(responseCode = "400", description = "Blank description, expiry not in the future, an invalid network or an unsupported scope",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "Administrator access is required", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @PostMapping(value = BASE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ApiTokenCreatedResponse> createApiToken(@Valid @RequestBody ApiTokenRequest request);

    @Operation(summary = "Delete (revoke) an API token", description = "The token stops working immediately", tags = "ApiTokenAPI")
    @Parameter(name = "token_id", example = "3", description = "ID of the token", required = true)
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Token deleted", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content),
        @ApiResponse(responseCode = "403", description = "Administrator access is required", content = @Content),
        @ApiResponse(responseCode = "404", description = "No such token", content = @Content) })
    @SecurityRequirement(name = "Bearer Authentication")
    @DeleteMapping(value = BASE_PATH + "/{token_id}")
    ResponseEntity<Void> deleteApiToken(@PathVariable("token_id") long tokenId);
}
