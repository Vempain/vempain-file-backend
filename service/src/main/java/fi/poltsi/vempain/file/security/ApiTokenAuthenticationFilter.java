package fi.poltsi.vempain.file.security;

import java.io.IOException;
import java.util.ArrayList;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import fi.poltsi.vempain.auth.service.UserDetailsImpl;
import fi.poltsi.vempain.auth.service.UserDetailsServiceImpl;
import fi.poltsi.vempain.auth.service.UserService;
import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.rest.ApiTokenAPI;
import fi.poltsi.vempain.file.rest.McpReadAPI;
import fi.poltsi.vempain.file.service.ApiTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates requests that carry a read-only API token in {@link ApiTokenAPI#API_TOKEN_HEADER}. A valid {@code MCP_READ} token authenticates the request as
 * the token's owner, but only for {@code GET /mcp} and {@code GET /mcp/**}; any other method or path answers 403 so that a leaked token can never change or
 * administer anything, and an invalid, expired or out-of-network token answers 401 without falling back to another credential. A {@code /mcp} request without a
 * token answers 403 as well, so a JWT session can never read the system-wide MCP view.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Authority marking a token authenticated request.
     */
    public static final String API_TOKEN_AUTHORITY = "API_TOKEN";

    /**
     * Authority of an {@code MCP_READ} token, required by the MCP read controller.
     */
    public static final String API_TOKEN_MCP_READ_AUTHORITY = "API_TOKEN_MCP_READ";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ApiTokenService apiTokenService;
    private final UserService userService;
    private final UserDetailsServiceImpl userDetailsService;
    private final FileMetrics fileMetrics;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        var presented = request.getHeader(ApiTokenAPI.API_TOKEN_HEADER);
        var path = requestPath(request);

        if (presented == null
            || presented.isBlank()) {
            if (isMcpPath(path)) {
                fileMetrics.apiTokenRequest("forbidden_endpoint");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "MCP_READ API token required");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        var token = apiTokenService.authenticate(presented, request.getRemoteAddr());

        if (token.isEmpty()) {
            // The reason (unknown, expired, wrong network) was counted by the token service
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API token");
            return;
        }

        var apiToken = token.get();
        if (!isAllowed(apiToken.getScope(), request.getMethod(), path)) {
            log.warn("API token {} used on {} {}, which tokens may not call", apiToken.getTokenPrefix(), request.getMethod(), path);
            fileMetrics.apiTokenRequest("forbidden_endpoint");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "API tokens may not call this endpoint");
            return;
        }

        var owner = userService.findById(apiToken.getOwnerUserId());
        if (owner.isEmpty()
            || owner.get()
                .isLocked()) {
            log.warn("API token {} belongs to a missing or locked user {}", apiToken.getTokenPrefix(), apiToken.getOwnerUserId());
            fileMetrics.apiTokenRequest("missing_user");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API token");
            return;
        }

        var principal = (UserDetailsImpl) userDetailsService.loadUserByUsername(owner.get()
            .getLoginName());
        var authorities = new ArrayList<GrantedAuthority>(principal.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(API_TOKEN_AUTHORITY));
        if (apiToken.getScope() == ApiTokenScopeEnum.MCP_READ) {
            authorities.add(new SimpleGrantedAuthority(API_TOKEN_MCP_READ_AUTHORITY));
        }
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext()
            .setAuthentication(authentication);
        fileMetrics.apiTokenRequest("accepted");
        filterChain.doFilter(request, response);
    }

    /**
     * Whether a token of {@code scope} may call {@code method path} (path relative to the context path): MCP_READ allows GET on {@code /mcp} and below only.
     */
    public static boolean isAllowed(ApiTokenScopeEnum scope, String method, String path) {
        return scope == ApiTokenScopeEnum.MCP_READ
               && HttpMethod.GET.matches(method)
               && isMcpPath(path);
    }

    static boolean isMcpPath(String path) {
        return McpReadAPI.BASE_PATH.equals(path)
               || PATH_MATCHER.match(McpReadAPI.BASE_PATH + "/**", path);
    }

    private static String requestPath(HttpServletRequest request) {
        var uri = request.getRequestURI();
        var context = request.getContextPath();
        var path = context != null
                   && !context.isEmpty()
                   && uri.startsWith(context) ? uri.substring(context.length()) : uri;
        return path.length() > 1
               && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
