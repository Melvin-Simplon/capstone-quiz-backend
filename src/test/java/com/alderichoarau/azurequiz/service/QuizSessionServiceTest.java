package com.alderichoarau.azurequiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.alderichoarau.azurequiz.dto.AnswerResultDto;
import com.alderichoarau.azurequiz.dto.CreateQuizSessionRequest;
import com.alderichoarau.azurequiz.dto.SubmitAnswerRequest;
import com.alderichoarau.azurequiz.entity.AnswerOption;
import com.alderichoarau.azurequiz.entity.Certification;
import com.alderichoarau.azurequiz.entity.Question;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizSessionServiceTest {
    @Mock private CertificationRepository certificationRepository;
    @Mock private QuizModuleRepository moduleRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private AnswerOptionRepository answerOptionRepository;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private QuizSessionQuestionRepository quizSessionQuestionRepository;
    @Mock private QuizAnswerRepository quizAnswerRepository;
    @Mock private QuizResultExportService quizResultExportService;

    private QuizSessionService service;

    @BeforeEach
    void setUp() {
        service =
                new QuizSessionService(
                        certificationRepository,
                        moduleRepository,
                        questionRepository,
                        answerOptionRepository,
                        quizSessionRepository,
                        quizSessionQuestionRepository,
                        quizAnswerRepository,
                        quizResultExportService);
    }

    @Test
    void createSession_moduleMode_missingModuleId_throwsInvalidRequest() {
        CreateQuizSessionRequest request =
                new CreateQuizSessionRequest(QuizMode.MODULE, null, null, null);

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void createSession_moduleMode_moduleNotFound_throwsResourceNotFound() {
        UUID moduleId = UUID.randomUUID();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.empty());
        CreateQuizSessionRequest request =
                new CreateQuizSessionRequest(QuizMode.MODULE, null, moduleId, null);

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSession_moduleMode_noActiveQuestions_throwsInvalidRequest() {
        UUID moduleId = UUID.randomUUID();
        Certification certification = Certification.builder().id(UUID.randomUUID()).code("AZ-900").build();
        QuizModule module =
                QuizModule.builder().id(moduleId).certification(certification).code("cloud-concepts").build();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(questionRepository.countByModuleIdAndActiveTrue(moduleId)).thenReturn(0L);
        CreateQuizSessionRequest request =
                new CreateQuizSessionRequest(QuizMode.MODULE, null, moduleId, null);

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void createSession_examMode_missingCertificationId_throwsInvalidRequest() {
        CreateQuizSessionRequest request = new CreateQuizSessionRequest(QuizMode.EXAM, null, null, null);

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void createSession_examMode_noQuestionsAvailable_throwsInvalidRequest() {
        UUID certificationId = UUID.randomUUID();
        Certification certification = Certification.builder().id(certificationId).code("AZ-900").build();
        when(certificationRepository.findById(certificationId)).thenReturn(Optional.of(certification));
        when(questionRepository.findRandomActiveByCertification(certificationId, 40)).thenReturn(List.of());
        CreateQuizSessionRequest request =
                new CreateQuizSessionRequest(QuizMode.EXAM, certificationId, null, null);

        assertThatThrownBy(() -> service.createSession(request))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void submitAnswer_sessionNotFound_throwsResourceNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(quizSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> service.submitAnswer(sessionId, questionId, new SubmitAnswerRequest(List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitAnswer_questionNotInSession_throwsInvalidRequest() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        when(quizSessionRepository.findById(sessionId))
                .thenReturn(Optional.of(QuizSession.builder().id(sessionId).build()));
        when(quizSessionQuestionRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.submitAnswer(
                                        sessionId, questionId, new SubmitAnswerRequest(List.of(UUID.randomUUID()))))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void submitAnswer_invalidOptionId_throwsInvalidRequest() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        QuizSession session = QuizSession.builder().id(sessionId).build();
        Question question =
                Question.builder().id(questionId).statement("Q").explanation("E").build();
        QuizSessionQuestion sessionQuestion =
                QuizSessionQuestion.builder().session(session).question(question).position(0).build();
        AnswerOption realOption =
                AnswerOption.builder().id(UUID.randomUUID()).question(question).correct(true).build();

        when(quizSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(quizSessionQuestionRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.of(sessionQuestion));
        when(answerOptionRepository.findByQuestionIdOrderByPositionAsc(questionId))
                .thenReturn(List.of(realOption));

        SubmitAnswerRequest request = new SubmitAnswerRequest(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.submitAnswer(sessionId, questionId, request))
                .isInstanceOf(InvalidQuizRequestException.class);
    }

    @Test
    void submitAnswer_exactCorrectSet_returnsCorrectTrue() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        QuizSession session = QuizSession.builder().id(sessionId).build();
        Question question =
                Question.builder().id(questionId).statement("Q").explanation("Because.").build();
        QuizSessionQuestion sessionQuestion =
                QuizSessionQuestion.builder().session(session).question(question).position(0).build();

        UUID correctId = UUID.randomUUID();
        UUID wrongId = UUID.randomUUID();
        AnswerOption correctOption =
                AnswerOption.builder().id(correctId).question(question).correct(true).build();
        AnswerOption wrongOption =
                AnswerOption.builder().id(wrongId).question(question).correct(false).build();

        when(quizSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(quizSessionQuestionRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.of(sessionQuestion));
        when(answerOptionRepository.findByQuestionIdOrderByPositionAsc(questionId))
                .thenReturn(List.of(correctOption, wrongOption));
        when(quizAnswerRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.empty());

        AnswerResultDto result =
                service.submitAnswer(sessionId, questionId, new SubmitAnswerRequest(List.of(correctId)));

        assertThat(result.correct()).isTrue();
        assertThat(result.correctOptionIds()).containsExactly(correctId);
        assertThat(result.explanation()).isEqualTo("Because.");
    }

    @Test
    void submitAnswer_wrongSelection_returnsCorrectFalse() {
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        QuizSession session = QuizSession.builder().id(sessionId).build();
        Question question = Question.builder().id(questionId).statement("Q").build();
        QuizSessionQuestion sessionQuestion =
                QuizSessionQuestion.builder().session(session).question(question).position(0).build();

        UUID correctId = UUID.randomUUID();
        UUID wrongId = UUID.randomUUID();
        AnswerOption correctOption =
                AnswerOption.builder().id(correctId).question(question).correct(true).build();
        AnswerOption wrongOption =
                AnswerOption.builder().id(wrongId).question(question).correct(false).build();

        when(quizSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(quizSessionQuestionRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.of(sessionQuestion));
        when(answerOptionRepository.findByQuestionIdOrderByPositionAsc(questionId))
                .thenReturn(List.of(correctOption, wrongOption));
        when(quizAnswerRepository.findBySessionIdAndQuestionId(sessionId, questionId))
                .thenReturn(Optional.empty());

        AnswerResultDto result =
                service.submitAnswer(sessionId, questionId, new SubmitAnswerRequest(List.of(wrongId)));

        assertThat(result.correct()).isFalse();
        assertThat(result.correctOptionIds()).containsExactly(correctId);
    }

    @Test
    void getResult_sessionNotFound_throwsResourceNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(quizSessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(sessionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
