package com.alderichoarau.azurequiz.service;

import com.alderichoarau.azurequiz.dto.AnswerOptionDto;
import com.alderichoarau.azurequiz.dto.AnswerResultDto;
import com.alderichoarau.azurequiz.dto.CreateQuizSessionRequest;
import com.alderichoarau.azurequiz.dto.QuestionDto;
import com.alderichoarau.azurequiz.dto.QuestionResultDto;
import com.alderichoarau.azurequiz.dto.QuizResultDto;
import com.alderichoarau.azurequiz.dto.QuizSessionDto;
import com.alderichoarau.azurequiz.dto.SubmitAnswerRequest;
import com.alderichoarau.azurequiz.entity.AnswerOption;
import com.alderichoarau.azurequiz.entity.Certification;
import com.alderichoarau.azurequiz.entity.Question;
import com.alderichoarau.azurequiz.entity.QuizAnswer;
import com.alderichoarau.azurequiz.entity.QuizMode;
import com.alderichoarau.azurequiz.entity.QuizModule;
import com.alderichoarau.azurequiz.entity.QuizSession;
import com.alderichoarau.azurequiz.entity.QuizSessionQuestion;
import com.alderichoarau.azurequiz.exception.InvalidQuizRequestException;
import com.alderichoarau.azurequiz.exception.ResourceNotFoundException;
import com.alderichoarau.azurequiz.repository.AnswerOptionRepository;
import com.alderichoarau.azurequiz.repository.CertificationRepository;
import com.alderichoarau.azurequiz.repository.QuestionRepository;
import com.alderichoarau.azurequiz.repository.QuizAnswerRepository;
import com.alderichoarau.azurequiz.repository.QuizModuleRepository;
import com.alderichoarau.azurequiz.repository.QuizSessionQuestionRepository;
import com.alderichoarau.azurequiz.repository.QuizSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class QuizSessionService {
    private static final int DEFAULT_EXAM_QUESTION_COUNT = 40;

    private final CertificationRepository certificationRepository;
    private final QuizModuleRepository moduleRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final QuizSessionQuestionRepository quizSessionQuestionRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final QuizResultExportService quizResultExportService;

    public QuizSessionDto createSession(CreateQuizSessionRequest request) {
        log.info(
                "Creating quiz session: mode={}, moduleId={}, certificationId={}, questionCount={}",
                request.mode(),
                request.moduleId(),
                request.certificationId(),
                request.questionCount());
        QuizModule module = null;
        Certification certification;
        List<Question> questions;

        if (request.mode() == QuizMode.MODULE) {
            if (request.moduleId() == null) {
                throw new InvalidQuizRequestException("moduleId is required for MODULE mode");
            }
            module =
                    moduleRepository
                            .findById(request.moduleId())
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Module not found: " + request.moduleId()));
            certification = module.getCertification();
            long available = questionRepository.countByModuleIdAndActiveTrue(module.getId());
            if (available == 0) {
                throw new InvalidQuizRequestException(
                        "No active questions available for module " + module.getCode());
            }
            int count =
                    request.questionCount() != null
                            ? Math.min(request.questionCount(), (int) available)
                            : (int) available;
            questions = questionRepository.findRandomActiveByModule(module.getId(), count);
        } else {
            if (request.certificationId() == null) {
                throw new InvalidQuizRequestException("certificationId is required for EXAM mode");
            }
            certification =
                    certificationRepository
                            .findById(request.certificationId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Certification not found: " + request.certificationId()));
            int count =
                    request.questionCount() != null
                            ? request.questionCount()
                            : DEFAULT_EXAM_QUESTION_COUNT;
            questions = questionRepository.findRandomActiveByCertification(certification.getId(), count);
            if (questions.isEmpty()) {
                throw new InvalidQuizRequestException(
                        "No active questions available for certification " + certification.getCode());
            }
        }

        QuizSession session =
                QuizSession.builder()
                        .mode(request.mode())
                        .certification(certification)
                        .module(module)
                        .questionCount(questions.size())
                        .createdAt(Instant.now())
                        .build();
        quizSessionRepository.save(session);

        List<QuizSessionQuestion> sessionQuestions = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            sessionQuestions.add(
                    QuizSessionQuestion.builder()
                            .session(session)
                            .question(questions.get(i))
                            .position(i)
                            .build());
        }
        quizSessionQuestionRepository.saveAll(sessionQuestions);

        List<QuestionDto> questionDtos = questions.stream().map(this::toQuestionDto).toList();

        log.info(
                "Created quiz session {} with {} questions (mode={}, certificationId={})",
                session.getId(),
                questionDtos.size(),
                session.getMode(),
                certification.getId());

        return new QuizSessionDto(
                session.getId(),
                session.getMode(),
                certification.getId(),
                module != null ? module.getId() : null,
                questionDtos);
    }

    public AnswerResultDto submitAnswer(
            UUID sessionId, UUID questionId, SubmitAnswerRequest request) {
        quizSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        QuizSessionQuestion sessionQuestion =
                quizSessionQuestionRepository
                        .findBySessionIdAndQuestionId(sessionId, questionId)
                        .orElseThrow(
                                () ->
                                        new InvalidQuizRequestException(
                                                "Question " + questionId + " does not belong to session " + sessionId));

        Question question = sessionQuestion.getQuestion();
        List<AnswerOption> options =
                answerOptionRepository.findByQuestionIdOrderByPositionAsc(questionId);

        Set<UUID> validOptionIds = options.stream().map(AnswerOption::getId).collect(Collectors.toSet());
        Set<UUID> selected = new HashSet<>(request.selectedOptionIds());
        if (!validOptionIds.containsAll(selected)) {
            throw new InvalidQuizRequestException("Selected options do not belong to this question");
        }

        Set<UUID> correctOptionIds =
                options.stream().filter(AnswerOption::isCorrect).map(AnswerOption::getId).collect(Collectors.toSet());
        boolean isCorrect = selected.equals(correctOptionIds);

        QuizAnswer answer =
                quizAnswerRepository
                        .findBySessionIdAndQuestionId(sessionId, questionId)
                        .orElseGet(
                                () ->
                                        QuizAnswer.builder()
                                                .session(sessionQuestion.getSession())
                                                .question(question)
                                                .build());
        answer.setSelectedOptionIds(selected);
        answer.setCorrect(isCorrect);
        answer.setAnsweredAt(Instant.now());
        quizAnswerRepository.save(answer);

        log.info(
                "Session {} answered question {}: correct={}",
                sessionId,
                questionId,
                isCorrect);

        return new AnswerResultDto(isCorrect, new ArrayList<>(correctOptionIds), question.getExplanation());
    }

    @Transactional(readOnly = true)
    public QuizResultDto getResult(UUID sessionId) {
        quizSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        List<QuizSessionQuestion> sessionQuestions =
                quizSessionQuestionRepository.findBySessionIdOrderByPositionAsc(sessionId);
        Map<UUID, QuizAnswer> answersByQuestionId =
                quizAnswerRepository.findBySessionId(sessionId).stream()
                        .collect(Collectors.toMap(a -> a.getQuestion().getId(), Function.identity()));

        List<QuestionResultDto> details =
                sessionQuestions.stream().map(sq -> toQuestionResultDto(sq, answersByQuestionId)).toList();

        int total = details.size();
        int answeredCount = (int) details.stream().filter(QuestionResultDto::answered).count();
        int correctCount = (int) details.stream().filter(QuestionResultDto::correct).count();
        double scorePercentage = total == 0 ? 0.0 : (correctCount * 100.0) / total;

        log.info(
                "Session {} result: {}/{} correct ({} answered, {}%)",
                sessionId,
                correctCount,
                total,
                answeredCount,
                String.format("%.1f", scorePercentage));

        QuizResultDto result =
                new QuizResultDto(sessionId, total, answeredCount, correctCount, scorePercentage, details);
        quizResultExportService.export(result);
        return result;
    }

    private QuestionResultDto toQuestionResultDto(
            QuizSessionQuestion sessionQuestion, Map<UUID, QuizAnswer> answersByQuestionId) {
        Question question = sessionQuestion.getQuestion();
        List<AnswerOption> options =
                answerOptionRepository.findByQuestionIdOrderByPositionAsc(question.getId());
        List<UUID> correctOptionIds =
                options.stream().filter(AnswerOption::isCorrect).map(AnswerOption::getId).toList();

        QuizAnswer answer = answersByQuestionId.get(question.getId());
        boolean answered = answer != null;
        boolean correct = answered && answer.isCorrect();
        List<UUID> selectedOptionIds =
                answered ? new ArrayList<>(answer.getSelectedOptionIds()) : List.of();

        return new QuestionResultDto(
                question.getId(),
                question.getStatement(),
                answered,
                correct,
                selectedOptionIds,
                correctOptionIds);
    }

    private QuestionDto toQuestionDto(Question question) {
        List<AnswerOption> options =
                answerOptionRepository.findByQuestionIdOrderByPositionAsc(question.getId());
        List<AnswerOption> shuffled = new ArrayList<>(options);
        Collections.shuffle(shuffled);
        List<AnswerOptionDto> optionDtos =
                shuffled.stream().map(o -> new AnswerOptionDto(o.getId(), o.getLabel())).toList();
        return new QuestionDto(question.getId(), question.getStatement(), question.getType(), optionDtos);
    }
}
