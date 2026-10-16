package fi.poltsi.vempain.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.GpsLocationEntity;

@Repository
public interface LocationRepository extends JpaRepository<GpsLocationEntity, Long> {
}
