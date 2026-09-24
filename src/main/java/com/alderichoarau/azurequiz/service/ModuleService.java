package com.alderichoarau.azurequiz.service;

import com.alderichoarau.azurequiz.dto.ModuleSummaryDto;
import com.alderichoarau.azurequiz.entity.QuizModule;
import com.alderichoarau.azurequiz.exception.ResourceNotFoundException;
import com.alderichoarau.azurequiz.repository.CertificationRepository;
import com.alderichoarau.azurequiz.repository.QuestionRepository;
import com.alderichoarau.azurequiz.repository.QuizModuleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModuleService {
    private final CertificationRepository certificationRepository;
    private final QuizModuleRepository moduleRepository;
    private final QuestionRepository questionRepository;

    @Cacheable(value = "modules", key = "#certificationId")
    public List<ModuleSummaryDto> getModulesByCertification(UUID certificationId) {
        log.debug("Fetching modules for certification {}", certificationId);
        if (!certificationRepository.existsById(certificationId)) {
            throw new ResourceNotFoundException("Certification not found: " + certificationId);
        }
        List<ModuleSummaryDto> modules =
                moduleRepository.findAllByCertificationIdOrderByPositionAsc(certificationId).stream()
                        .map(this::toSummaryDto)
                        .toList();
        log.debug("Found {} modules for certification {}", modules.size(), certificationId);
        return modules;
    }

    private ModuleSummaryDto toSummaryDto(QuizModule module) {
        long questionCount = questionRepository.countByModuleIdAndActiveTrue(module.getId());
        return new ModuleSummaryDto(
                module.getId(),
                module.getCode(),
                module.getTitle(),
                module.getDescription(),
                module.getPosition(),
                module.getType(),
                questionCount);
    }
}
