package fi.poltsi.vempain.file.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import fi.poltsi.vempain.auth.entity.UserAccount;
import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.auth.service.UserDetailsServiceImpl;
import fi.poltsi.vempain.auth.service.UserService;
import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.file.entity.ApiToken;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.rest.ApiTokenAPI;
import fi.poltsi.vempain.file.service.ApiTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The token filter: a valid MCP_READ token on {@code GET /mcp/**} authenticates as the owner with the {@code API_TOKEN} and {@code API_TOKEN_MCP_READ}
 * authorities, any other method or path is forbidden, an invalid token or a missing or locked owner is unauthorized, and {@code /mcp} without a token is
 * forbidden so that a JWT session never reaches it. Paths are compared without the context path and a trailing slash.
 */
@ExtendWith(MockitoExtension.class)
class ApiTokenAuthenticationFilterUTC {

    @Mock
    private ApiTokenService apiTokenService;
    @Mock
    private UserService userService;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private FilterChain filterChain;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private ApiTokenAuthenticationFilter filter() {
        return new ApiTokenAuthenticationFilter(apiTokenService, userService, userDetailsService, new FileMetrics(registry));
    }

    private double counted(String result) {
        var counter = registry.find(FileMetrics.API_TOKEN_REQUESTS)
            .tag("result", result)
            .counter();
        return counter == null ? 0 : counter.count();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        var request = new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        request.setRemoteAddr("10.0.9.5");
        request.addHeader(ApiTokenAPI.API_TOKEN_HEADER, "vft_secret");
        return request;
    }

    private static ApiToken token() {
        return ApiToken.builder()
            .id(1L)
            .tokenPrefix("vft_sec")
            .ownerUserId(5L)
            .scope(ApiTokenScopeEnum.MCP_READ)
            .expiresAt(Instant.now()
                .plusSeconds(3600))
            .network("10.0.9.0/24")
            .build();
    }

    private void ownerExists(boolean locked) {
        when(userService.findById(5L)).thenReturn(Optional.of(UserAccount.builder()
            .id(5L)
            .loginName("admin")
            .locked(locked)
            .build()));
    }

    @Test
    void withoutATokenHeaderANonMcpRequestPassesThroughUntouched() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/files/archive/1");
        request.setContextPath("/api");
        var response = new MockHttpServletResponse();

        filter().doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(apiTokenService, never()).authenticate(anyString(), anyString());
        assertNull(SecurityContextHolder.getContext()
            .getAuthentication());
    }

    @Test
    void mcpPathWithoutAnApiTokenIsForbiddenBeforeJwtAuthentication() throws Exception {
        for (var uri : List.of("/api/mcp", "/api/mcp/files", "/api/mcp/files/")) {
            var request = new MockHttpServletRequest("GET", uri);
            request.setContextPath("/api");
            request.addHeader("Authorization", "Bearer eyJ...");
            var response = new MockHttpServletResponse();

            filter().doFilterInternal(request, response, filterChain);

            assertEquals(403, response.getStatus(), uri);
        }
        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(3, counted("forbidden_endpoint"));
    }

    @Test
    void aMcpReadTokenAuthenticatesAsTheOwnerWithTheMcpAuthorityEvenWithATrailingSlash() throws Exception {
        when(apiTokenService.authenticate("vft_secret", "10.0.9.5")).thenReturn(Optional.of(token()));
        ownerExists(false);
        var principal = new UserDetailsImpl(5L, "admin", "Admin", "admin@nohost.nodomain", "secret", Set.of(), List.of());
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(principal);
        var request = request("GET", "/api/mcp/files/");
        var response = new MockHttpServletResponse();

        filter().doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var authentication = SecurityContextHolder.getContext()
            .getAuthentication();
        assertNotNull(authentication);
        assertEquals(principal, authentication.getPrincipal());
        var authorities = authentication.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        assertTrue(authorities.contains(ApiTokenAuthenticationFilter.API_TOKEN_AUTHORITY));
        assertTrue(authorities.contains(ApiTokenAuthenticationFilter.API_TOKEN_MCP_READ_AUTHORITY));
        assertNotNull(authentication.getDetails());
        assertEquals(1, counted("accepted"));
    }

    @Test
    void aTokenCannotWriteOrLeaveTheMcpEndpoints() throws Exception {
        when(apiTokenService.authenticate("vft_secret", "10.0.9.5")).thenReturn(Optional.of(token()));
        for (var call : List.of(List.of("POST", "/api/mcp/files"), List.of("DELETE", "/api/mcp/files/1"), List.of("PUT", "/api/mcp/tags"),
                List.of("GET", "/api/files/archive/1"), List.of("GET", "/api/api-tokens"), List.of("GET", "/api/mcpx"))) {
            var response = new MockHttpServletResponse();

            filter().doFilterInternal(request(call.get(0), call.get(1)), response, filterChain);

            assertEquals(403, response.getStatus(), call.toString());
        }
        verify(userService, never()).findById(anyLong());
        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(6, counted("forbidden_endpoint"));
    }

    @Test
    void anInvalidTokenIsUnauthorized() throws Exception {
        when(apiTokenService.authenticate("vft_secret", "10.0.9.5")).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();

        filter().doFilterInternal(request("GET", "/api/mcp/files"), response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void aTokenWhoseOwnerIsMissingIsUnauthorized() throws Exception {
        when(apiTokenService.authenticate("vft_secret", "10.0.9.5")).thenReturn(Optional.of(token()));
        when(userService.findById(5L)).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();

        filter().doFilterInternal(request("GET", "/api/mcp/files"), response, filterChain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext()
            .getAuthentication());
        assertEquals(1, counted("missing_user"));
    }

    @Test
    void aTokenWhoseOwnerIsLockedIsUnauthorized() throws Exception {
        when(apiTokenService.authenticate("vft_secret", "10.0.9.5")).thenReturn(Optional.of(token()));
        ownerExists(true);
        var response = new MockHttpServletResponse();

        filter().doFilterInternal(request("GET", "/api/mcp/files"), response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void theScopeAllowsOnlyGetOnTheMcpPaths() {
        assertTrue(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "GET", "/mcp"));
        assertTrue(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "GET", "/mcp/files/12"));
        assertFalse(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "POST", "/mcp/files"));
        assertFalse(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "HEAD", "/mcp/files"));
        assertFalse(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "GET", "/statistics"));
        assertFalse(ApiTokenAuthenticationFilter.isAllowed(ApiTokenScopeEnum.MCP_READ, "GET", "/mcp-other"));
        assertFalse(ApiTokenAuthenticationFilter.isAllowed(null, "GET", "/mcp/files"));
    }
}
