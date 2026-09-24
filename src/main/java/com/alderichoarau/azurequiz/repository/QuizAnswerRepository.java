package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.QuizAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {
    Optional<QuizAnswer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

    List<QuizAnswer> findBySessionId(UUID sessionId);
}
