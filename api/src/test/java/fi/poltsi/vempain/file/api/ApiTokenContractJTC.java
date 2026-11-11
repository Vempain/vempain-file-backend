package fi.poltsi.vempain.file.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.common.api.request.ApiTokenRequest;
import fi.poltsi.vempain.common.api.response.ApiTokenCreatedResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenNetworkResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenResponse;
import fi.poltsi.vempain.file.api.response.McpPagedResponse;
import fi.poltsi.vempain.file.rest.ApiTokenAPI;
import fi.poltsi.vempain.file.rest.McpReadAPI;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JSON contract of the read-only API token management and the MCP read pages.
 */
class ApiTokenContractJTC {

    private final JsonMapper mapper = JsonMapper.builder()
        .build();

    @Test
    void pathsAndHeaderRemainStable() {
        assertEquals("/api-tokens", ApiTokenAPI.BASE_PATH);
        assertEquals("/mcp", McpReadAPI.BASE_PATH);
        assertEquals("X-Vempain-Api-Token", ApiTokenAPI.API_TOKEN_HEADER);
    }

    @Test
    void requestIsSnakeCaseAndUsesTheSharedScopeContract() {
        var request = mapper.readValue(
                "{\"description\": \"d\", \"network\": \"10.0.0.0/8\", \"expires_at\": \"2027-01-01T00:00:00Z\", " + "\"scope\": \"MCP_READ\"}",
                ApiTokenRequest.class);
        assertEquals("10.0.0.0/8", request.getNetwork());
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), request.getExpiresAt());
        assertEquals(ApiTokenScopeEnum.MCP_READ, request.getScope());

        var withoutScope = mapper.readValue("{\"description\": \"d\", \"network\": \"10.0.0.0/8\", \"expires_at\": \"2027-01-01T00:00:00Z\"}",
                ApiTokenRequest.class);
        assertNull(withoutScope.getScope());

        var sharedScope = mapper.readValue(
                "{\"description\": \"d\", \"network\": \"10.0.0.0/8\", " + "\"expires_at\": \"2027-01-01T00:00:00Z\", \"scope\": \"FILE_SERVICE\"}",
                ApiTokenRequest.class);
        assertEquals(ApiTokenScopeEnum.FILE_SERVICE, sharedScope.getScope());
    }

    @Test
    void responsesAreSnakeCase() {
        var response = ApiTokenResponse.builder()
            .id(1)
            .tokenPrefix("vft_abc")
            .description("d")
            .scope(ApiTokenScopeEnum.MCP_READ)
            .network("10.0.0.0/8")
            .expiresAt(Instant.parse("2027-01-01T00:00:00Z"))
            .ownerUserId(1)
            .created(Instant.now())
            .build();
        var keys = new HashSet<>(mapper.readTree(mapper.writeValueAsString(response))
            .propertyNames());
        assertEquals(Set.of("id", "token_prefix", "description", "scope", "network", "expires_at", "owner_user_id", "created", "last_used", "expired"), keys);

        var created = ApiTokenCreatedResponse.builder()
            .token("vft_secret")
            .apiToken(response)
            .build();
        assertEquals(Set.of("token", "api_token"), new HashSet<>(mapper.readTree(mapper.writeValueAsString(created))
            .propertyNames()));

        var page = new McpPagedResponse<>(List.of("a"), 0, 100, 1);
        assertEquals(Set.of("items", "page", "size", "total_elements"), new HashSet<>(mapper.readTree(mapper.writeValueAsString(page))
            .propertyNames()));
    }

    @Test
    void networkSuggestionIsSnakeCase() {
        var response = ApiTokenNetworkResponse.builder()
            .network("10.0.9.0/24")
            .source(ApiTokenNetworkResponse.Source.PRIVATE_NETWORK)
            .candidates(List.of(ApiTokenNetworkResponse.Candidate.builder()
                .network("10.0.9.0/24")
                .interfaceName("eth1")
                .address("10.0.9.5")
                .build()))
            .build();
        var tree = mapper.readTree(mapper.writeValueAsString(response));
        assertEquals(Set.of("network", "source", "candidates"), new HashSet<>(tree.propertyNames()));
        assertEquals(Set.of("network", "interface_name", "address"), new HashSet<>(tree.path("candidates")
            .get(0)
            .propertyNames()));
        assertEquals("PRIVATE_NETWORK", tree.path("source")
            .asString());
    }
}
