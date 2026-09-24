package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.QuizSessionQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSessionQuestionRepository extends JpaRepository<QuizSessionQuestion, UUID> {
    List<QuizSessionQuestion> findBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<QuizSessionQuestion> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
}
