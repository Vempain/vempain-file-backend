package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.GpsLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GpsLocationRepository extends JpaRepository<GpsLocationEntity, Long> {
	Optional<GpsLocationEntity> findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(Double latitude, Character latitudeRef, Double longitude, Character longitudeRef);
}
