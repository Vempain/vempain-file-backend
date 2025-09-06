package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GpsLocationRepository extends JpaRepository<GpsLocationEntity, Long> {
}
