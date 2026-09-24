package com.alderichoarau.azurequiz.repository;

import com.alderichoarau.azurequiz.entity.QuizModule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizModuleRepository extends JpaRepository<QuizModule, UUID> {
    List<QuizModule> findAllByCertificationIdOrderByPositionAsc(UUID certificationId);
}
