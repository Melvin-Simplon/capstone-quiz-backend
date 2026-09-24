package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.AnswerOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, UUID> {
    List<AnswerOption> findByQuestionIdOrderByPositionAsc(UUID questionId);

    List<AnswerOption> findByQuestionIdInOrderByPositionAsc(List<UUID> questionIds);
}
