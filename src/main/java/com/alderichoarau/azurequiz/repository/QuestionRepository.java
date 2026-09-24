package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.Question;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    long countByModuleIdAndActiveTrue(UUID moduleId);

    @Query(
            value =
                    "SELECT * FROM question WHERE module_id = :moduleId AND active = true ORDER BY"
                            + " random() LIMIT :limit",
            nativeQuery = true)
    List<Question> findRandomActiveByModule(
            @Param("moduleId") UUID moduleId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT q.* FROM question q JOIN module m ON q.module_id = m.id"
                            + " WHERE m.certification_id = :certificationId AND m.type = 'CONTENT'"
                            + " AND q.active = true ORDER BY random() LIMIT :limit",
            nativeQuery = true)
    List<Question> findRandomActiveByCertification(
            @Param("certificationId") UUID certificationId, @Param("limit") int limit);

    @Query(
            value =
                    "SELECT count(*) FROM question q JOIN module m ON q.module_id = m.id"
                            + " WHERE m.certification_id = :certificationId AND m.type = 'CONTENT'"
                            + " AND q.active = true",
            nativeQuery = true)
    long countActiveByCertification(@Param("certificationId") UUID certificationId);
}
