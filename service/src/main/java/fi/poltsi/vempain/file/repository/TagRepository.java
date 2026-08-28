package fi.poltsi.vempain.file.repository;

import fi.poltsi.vempain.file.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long>, JpaSpecificationExecutor<TagEntity> {
	Optional<TagEntity> findByTagName(String tagName);

	@Query(value = "SELECT id FROM tag_mutation_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
	Long lockTagMutations();
}
