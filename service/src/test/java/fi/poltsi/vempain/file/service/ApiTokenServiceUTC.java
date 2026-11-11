package fi.poltsi.vempain.file.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.common.api.request.ApiTokenRequest;
import fi.poltsi.vempain.common.api.response.ApiTokenNetworkResponse;
import fi.poltsi.vempain.file.entity.ApiToken;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.ApiTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceUTC {

    @Mock
    private ApiTokenRepository apiTokenRepository;
    @Spy
    private FileMetrics fileMetrics = new FileMetrics(new SimpleMeterRegistry());
    @InjectMocks
    private ApiTokenService apiTokenService;

    private static ApiTokenRequest request(String network, Instant expiresAt) {
        return ApiTokenRequest.builder()
            .description("vempain-mcp")
            .network(network)
            .expiresAt(expiresAt)
            .build();
    }

    @Test
    void createStoresOnlyTheHashAndReturnsTheTokenOnce() {
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            token.setId(5L);
            return token;
        });

        var created = apiTokenService.create(request("10.1.2.3", Instant.now()
            .plus(30, ChronoUnit.DAYS)), 1L);

        assertTrue(created.getToken()
            .startsWith("vft_"));
        assertTrue(created.getToken()
            .length() > 40);
        var captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenRepository).save(captor.capture());
        var stored = captor.getValue();
        assertEquals(ApiTokenService.hash(created.getToken()), stored.getTokenHash());
        assertNotEquals(created.getToken(), stored.getTokenHash());
        assertEquals(64, stored.getTokenHash()
            .length());
        assertEquals("10.1.2.3/32", stored.getNetwork(), "a single address is stored as a host network");
        assertEquals(1L, stored.getOwnerUserId());
        assertEquals(1L, stored.getCreatedBy());
        assertEquals(ApiTokenScopeEnum.MCP_READ, stored.getScope(), "a missing scope means MCP_READ");
        assertEquals(created.getToken()
            .substring(0, 12), stored.getTokenPrefix());
        assertEquals(5L, created.getApiToken()
            .getId());
        assertEquals(ApiTokenScopeEnum.MCP_READ, created.getApiToken()
            .getScope());
        assertFalse(created.getApiToken()
            .isExpired());
    }

    @Test
    void createAcceptsTheExplicitMcpReadScope() {
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> {
            ApiToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });
        var request = request("2001:db8::/32", Instant.now()
            .plus(1, ChronoUnit.DAYS));
        request.setScope(ApiTokenScopeEnum.MCP_READ);

        var created = apiTokenService.create(request, 1L);

        assertEquals(ApiTokenScopeEnum.MCP_READ, created.getApiToken()
            .getScope());
        assertEquals("2001:db8::/32", created.getApiToken()
            .getNetwork());
    }

    @Test
    void createRejectsPastExpiryBadNetworkAndBlankDescription() {
        var future = Instant.now()
            .plus(1, ChronoUnit.DAYS);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> apiTokenService.create(request("10.1.2.3", Instant.now()
            .minus(1, ChronoUnit.MINUTES)), 1L)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> apiTokenService.create(request("10.1.2.3", null), 1L)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> apiTokenService.create(request("vempain-mcp.example.com", future), 1L)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> apiTokenService.create(request("10.0.0.0/40", future), 1L)).getStatusCode());
        var blank = request("10.1.2.3", future);
        blank.setDescription("  ");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> apiTokenService.create(blank, 1L)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> apiTokenService.create(null, 1L)).getStatusCode());
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    void authenticateAcceptsOnlyLiveTokensFromTheirNetwork() {
        var token = "vft_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        var stored = ApiToken.builder()
            .id(7L)
            .tokenHash(ApiTokenService.hash(token))
            .tokenPrefix("vft_abcdefgh")
            .network("10.1.2.0/24")
            .expiresAt(Instant.now()
                .plus(1, ChronoUnit.HOURS))
            .ownerUserId(1L)
            .build();
        when(apiTokenRepository.findByTokenHash(ApiTokenService.hash(token))).thenReturn(Optional.of(stored));
        when(apiTokenRepository.save(any(ApiToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(apiTokenService.authenticate(token, "10.1.2.77")
            .isPresent());
        assertNotNull(stored.getLastUsed(), "a successful use is recorded");
        assertTrue(apiTokenService.authenticate(token, "10.1.3.1")
            .isEmpty(), "outside the network");
        assertTrue(apiTokenService.authenticate(token, "2001:db8::1")
            .isEmpty(), "IPv6 client against an IPv4 network");

        stored.setExpiresAt(Instant.now()
            .minus(1, ChronoUnit.SECONDS));
        assertTrue(apiTokenService.authenticate(token, "10.1.2.77")
            .isEmpty(), "expired");
        verify(fileMetrics, times(2)).apiTokenRequest("wrong_network");
        verify(fileMetrics).apiTokenRequest("expired");
        verify(fileMetrics, never()).apiTokenRequest("accepted");
    }

    @Test
    void lastUseIsRecordedAtMostOnceAMinute() {
        var token = "vft_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        var recently = Instant.now()
            .minusSeconds(10);
        var stored = ApiToken.builder()
            .id(7L)
            .tokenHash(ApiTokenService.hash(token))
            .network("10.1.2.0/24")
            .expiresAt(Instant.now()
                .plus(1, ChronoUnit.HOURS))
            .lastUsed(recently)
            .build();
        when(apiTokenRepository.findByTokenHash(ApiTokenService.hash(token))).thenReturn(Optional.of(stored));

        assertTrue(apiTokenService.authenticate(token, "10.1.2.77")
            .isPresent());
        assertEquals(recently, stored.getLastUsed());
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    void authenticateRejectsUnknownAndMalformedTokensWithoutQueryingNonsense() {
        when(apiTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertTrue(apiTokenService.authenticate("vft_unknown", "10.1.2.3")
            .isEmpty());
        assertTrue(apiTokenService.authenticate(null, "10.1.2.3")
            .isEmpty());
        assertTrue(apiTokenService.authenticate("", "10.1.2.3")
            .isEmpty());
        assertTrue(apiTokenService.authenticate("vat_admin-token", "10.1.2.3")
            .isEmpty(), "an admin backend token is never looked up here");
        assertTrue(apiTokenService.authenticate("Bearer eyJ...", "10.1.2.3")
            .isEmpty(), "a JWT is never looked up as a token");
        verify(apiTokenRepository, times(1)).findByTokenHash(anyString());
        verify(fileMetrics, times(5)).apiTokenRequest("unknown");
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    void deleteRemovesTheTokenOrAnswersNotFound() {
        var stored = ApiToken.builder()
            .id(3L)
            .tokenPrefix("vft_x")
            .description("d")
            .build();
        when(apiTokenRepository.findById(3L)).thenReturn(Optional.of(stored));
        when(apiTokenRepository.findById(4L)).thenReturn(Optional.empty());

        apiTokenService.delete(3L);
        verify(apiTokenRepository).delete(stored);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> apiTokenService.delete(4L)).getStatusCode());
    }

    @Test
    void findAllListsTheTokensWithoutTheirHash() {
        when(apiTokenRepository.findAllByOrderByCreatedDesc()).thenReturn(List.of(ApiToken.builder()
            .id(2L)
            .tokenHash("secret-hash")
            .tokenPrefix("vft_abc")
            .network("10.0.0.0/8")
            .expiresAt(Instant.now()
                .minusSeconds(1))
            .build()));

        var tokens = apiTokenService.findAll();

        assertEquals(1, tokens.size());
        assertEquals("vft_abc", tokens.getFirst()
            .getTokenPrefix());
        assertTrue(tokens.getFirst()
            .isExpired());
        assertFalse(tokens.getFirst()
            .toString()
            .contains("secret-hash"));
    }

    @Test
    void defaultNetworkFollowsTheConfigurationAndTheDetectedPrivateNetworks() {
        var none = apiTokenService.defaultNetwork();
        assertEquals(ApiTokenNetworkResponse.Source.NONE, none.getSource());
        assertNull(none.getNetwork());
        assertTrue(none.getCandidates()
            .isEmpty());

        ReflectionTestUtils.setField(apiTokenService, "configuredDefaultNetwork", " 10.20.0.0/16 ");
        var configured = apiTokenService.defaultNetwork();
        assertEquals(ApiTokenNetworkResponse.Source.CONFIGURED, configured.getSource());
        assertEquals("10.20.0.0/16", configured.getNetwork());

        ReflectionTestUtils.setField(apiTokenService, "configuredDefaultNetwork", "not-a-network");
        assertEquals(ApiTokenNetworkResponse.Source.NONE, apiTokenService.defaultNetwork()
            .getSource());

        ReflectionTestUtils.setField(apiTokenService, "configuredDefaultNetwork", "");
        ReflectionTestUtils.setField(apiTokenService, "privateNetwork", true);
        var detected = apiTokenService.defaultNetwork();
        if (detected.getCandidates()
            .isEmpty()) {
            assertEquals(ApiTokenNetworkResponse.Source.NONE, detected.getSource());
        } else {
            assertEquals(ApiTokenNetworkResponse.Source.PRIVATE_NETWORK, detected.getSource());
            assertEquals(detected.getCandidates()
                .getFirst()
                .getNetwork(), detected.getNetwork());
        }
    }
}
