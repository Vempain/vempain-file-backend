package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.LocationGuardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationGuardRepository extends JpaRepository<LocationGuardEntity, Long> {
}

