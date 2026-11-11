package fi.poltsi.vempain.file.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.common.api.request.ApiTokenRequest;
import fi.poltsi.vempain.common.api.response.ApiTokenCreatedResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenNetworkResponse;
import fi.poltsi.vempain.common.api.response.ApiTokenResponse;
import fi.poltsi.vempain.common.security.ApiTokenCrypto;
import fi.poltsi.vempain.common.security.NetworkMatcher;
import fi.poltsi.vempain.common.security.PrivateNetworkDetector;
import fi.poltsi.vempain.file.entity.ApiToken;
import fi.poltsi.vempain.file.metrics.FileMetrics;
import fi.poltsi.vempain.file.repository.ApiTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only API tokens for vempain-mcp. A token is 32 random bytes (base64url) behind the {@code vft_} prefix; only its SHA-256 hash is stored, so a database
 * leak does not leak usable tokens. A token is valid while it has not expired and the request comes from its network, and it acts as the administrator who
 * created it. The only scope is {@link ApiTokenScopeEnum#MCP_READ}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiTokenService {

    public static final String TOKEN_PREFIX = "vft_";
    private static final int VISIBLE_PREFIX = 8;
    private static final Duration LAST_USED_INTERVAL = Duration.ofMinutes(1);

    private final ApiTokenRepository apiTokenRepository;
    private final FileMetrics fileMetrics;
    /**
     * True when the services run on a private Docker/Swarm network together: the token network is then pre-filled with the private network the file backend
     * itself is attached to.
     */
    @Value("${vempain.file.api-token.private-network:false}")
    private boolean privateNetwork;

    /**
     * Explicit default network (CIDR) for deployments that span hosts; wins over the detected one.
     */
    @Value("${vempain.file.api-token.default-network:}")
    private String configuredDefaultNetwork;

    /**
     * The network to propose for a new token, with every private network this process is attached to as alternatives.
     */
    public ApiTokenNetworkResponse defaultNetwork() {
        var candidates = privateNetwork ? PrivateNetworkDetector.detect()
            .stream()
            .map(found -> ApiTokenNetworkResponse.Candidate.builder()
                .network(found.network())
                .interfaceName(found.interfaceName())
                .address(found.address())
                .build())
            .toList() : List.<ApiTokenNetworkResponse.Candidate>of();

        if (configuredDefaultNetwork != null
            && !configuredDefaultNetwork.isBlank()) {
            try {
                return ApiTokenNetworkResponse.builder()
                    .network(NetworkMatcher.normalize(configuredDefaultNetwork))
                    .source(ApiTokenNetworkResponse.Source.CONFIGURED)
                    .candidates(candidates)
                    .build();
            } catch (IllegalArgumentException e) {
                log.error("vempain.file.api-token.default-network is not a valid network: {}", e.getMessage());
            }
        }

        if (!candidates.isEmpty()) {
            return ApiTokenNetworkResponse.builder()
                .network(candidates.getFirst()
                    .getNetwork())
                .source(ApiTokenNetworkResponse.Source.PRIVATE_NETWORK)
                .candidates(candidates)
                .build();
        }

        return ApiTokenNetworkResponse.builder()
            .network(null)
            .source(ApiTokenNetworkResponse.Source.NONE)
            .candidates(candidates)
            .build();
    }

    public List<ApiTokenResponse> findAll() {
        return apiTokenRepository.findAllByOrderByCreatedDesc()
            .stream()
            .map(ApiToken::toResponse)
            .toList();
    }

    /**
     * Creates a token owned by {@code creatorUserId}.
     *
     * @throws ResponseStatusException 400 when the description is blank, the expiry is not in the future, the network is not a valid IPv4/IPv6 address or CIDR
     *                                 network, or the scope is not MCP_READ
     */
    @Transactional
    public ApiTokenCreatedResponse create(ApiTokenRequest request, long creatorUserId) {
        if (request == null
            || request.getDescription() == null
            || request.getDescription()
                .isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }
        if (request.getExpiresAt() == null
            || !request.getExpiresAt()
                .isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration must be in the future");
        }
        var scope = request.getScope() == null ? ApiTokenScopeEnum.MCP_READ : request.getScope();
        if (scope != ApiTokenScopeEnum.MCP_READ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file backend only issues MCP_READ tokens");
        }
        String network;
        try {
            network = NetworkMatcher.normalize(request.getNetwork());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid network: " + e.getMessage());
        }

        var token = ApiTokenCrypto.generate(TOKEN_PREFIX);
        var entity = apiTokenRepository.save(ApiToken.builder()
            .tokenHash(ApiTokenCrypto.hash(token))
            .tokenPrefix(token.substring(0, TOKEN_PREFIX.length() + VISIBLE_PREFIX))
            .description(request.getDescription()
                .trim())
            .scope(scope)
            .network(network)
            .expiresAt(request.getExpiresAt())
            .ownerUserId(creatorUserId)
            .createdBy(creatorUserId)
            .created(Instant.now())
            .build());
        log.info("API token {} created by user {} for network {} until {}", entity.getTokenPrefix(), creatorUserId, network, entity.getExpiresAt());

        return ApiTokenCreatedResponse.builder()
            .token(token)
            .apiToken(entity.toResponse())
            .build();
    }

    /**
     * @throws ResponseStatusException 404 when no such token exists
     */
    @Transactional
    public void delete(long tokenId) {
        var token = apiTokenRepository.findById(tokenId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such API token"));
        apiTokenRepository.delete(token);
        log.info("API token {} ({}) deleted", token.getTokenPrefix(), token.getDescription());
    }

    /**
     * Resolves a presented token: it must exist, not be expired and the client address must lie in its network. A successful use is recorded in
     * {@code last_used} (at most once a minute).
     *
     * @return the token when the request may proceed, empty otherwise (the reason is logged, never revealed to the caller)
     */
    @Transactional
    public Optional<ApiToken> authenticate(String presentedToken, String clientAddress) {
        if (presentedToken == null
            || presentedToken.isBlank()
            || !presentedToken.startsWith(TOKEN_PREFIX)) {
            fileMetrics.apiTokenRequest("unknown");
            return Optional.empty();
        }

        var optionalToken = apiTokenRepository.findByTokenHash(ApiTokenCrypto.hash(presentedToken.trim()));
        if (optionalToken.isEmpty()) {
            log.warn("Unknown API token presented from {}", clientAddress);
            fileMetrics.apiTokenRequest("unknown");
            return Optional.empty();
        }

        var token = optionalToken.get();
        if (!token.getExpiresAt()
            .isAfter(Instant.now())) {
            log.warn("Expired API token {} presented from {}", token.getTokenPrefix(), clientAddress);
            fileMetrics.apiTokenRequest("expired");
            return Optional.empty();
        }
        if (!NetworkMatcher.matches(token.getNetwork(), clientAddress)) {
            log.warn("API token {} presented from {} which is outside its network {}", token.getTokenPrefix(), clientAddress, token.getNetwork());
            fileMetrics.apiTokenRequest("wrong_network");
            return Optional.empty();
        }

        var now = Instant.now();
        if (token.getLastUsed() == null
            || token.getLastUsed()
                .plus(LAST_USED_INTERVAL)
                .isBefore(now)) {
            token.setLastUsed(now);
            apiTokenRepository.save(token);
        }
        return Optional.of(token);
    }

    public static String hash(String token) {
        return ApiTokenCrypto.hash(token);
    }
}
