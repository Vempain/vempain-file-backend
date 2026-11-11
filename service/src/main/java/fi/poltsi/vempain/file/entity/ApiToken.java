package fi.poltsi.vempain.file.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import fi.poltsi.vempain.common.api.ApiTokenScopeEnum;
import fi.poltsi.vempain.common.api.response.ApiTokenResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A read-only API token. {@code tokenHash} is the SHA-256 of the token string, which is never stored. Tokens carry no ACL; they are managed by administrators
 * only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_token")
@ToString(exclude = "tokenHash")
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "description", nullable = false)
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private ApiTokenScopeEnum scope = ApiTokenScopeEnum.MCP_READ;

    @Column(name = "network", nullable = false, length = 64)
    private String network;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Column(name = "created", nullable = false)
    private Instant created;

    @Column(name = "last_used")
    private Instant lastUsed;

    public ApiTokenResponse toResponse() {
        return ApiTokenResponse.builder()
            .id(id)
            .tokenPrefix(tokenPrefix)
            .description(description)
            .scope(scope)
            .network(network)
            .expiresAt(expiresAt)
            .ownerUserId(ownerUserId)
            .created(created)
            .lastUsed(lastUsed)
            .expired(expiresAt != null
                     && !expiresAt.isAfter(Instant.now()))
            .build();
    }
}
