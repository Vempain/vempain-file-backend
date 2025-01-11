package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {
	Optional<TagEntity> findByTagName(String tagName);
}
