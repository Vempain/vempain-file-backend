package fi.poltsi.vempain.file.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.ApiToken;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findAllByOrderByCreatedDesc();
}
