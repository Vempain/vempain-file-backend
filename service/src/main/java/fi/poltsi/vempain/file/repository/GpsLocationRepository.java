package fi.poltsi.vempain.file.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import fi.poltsi.vempain.file.entity.GpsLocationEntity;

@Repository
public interface GpsLocationRepository extends JpaRepository<GpsLocationEntity, Long> {

    Optional<GpsLocationEntity> findByLatitudeAndLatitudeRefAndLongitudeAndLongitudeRef(BigDecimal latitude, Character latitudeRef, BigDecimal longitude,
            Character longitudeRef);
}
