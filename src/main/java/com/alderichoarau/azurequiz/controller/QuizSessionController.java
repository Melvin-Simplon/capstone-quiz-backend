package com.alderichoarau.azurequiz.controller;

import com.alderichoarau.azurequiz.dto.AnswerResultDto;
import com.alderichoarau.azurequiz.dto.CreateQuizSessionRequest;
import com.alderichoarau.azurequiz.dto.QuizResultDto;
import com.alderichoarau.azurequiz.dto.QuizSessionDto;
import com.alderichoarau.azurequiz.dto.SubmitAnswerRequest;
import com.alderichoarau.azurequiz.service.QuizResultExportService;
import com.alderichoarau.azurequiz.service.QuizSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quiz-sessions")
@RequiredArgsConstructor
public class QuizSessionController {

    private final QuizSessionService quizSessionService;
    private final QuizResultExportService quizResultExportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuizSessionDto createSession(@Valid @RequestBody CreateQuizSessionRequest request) {
        return quizSessionService.createSession(request);
    }

    @PostMapping("/{sessionId}/questions/{questionId}/answer")
    public AnswerResultDto submitAnswer(
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        return quizSessionService.submitAnswer(sessionId, questionId, request);
    }

    @GetMapping("/{sessionId}/result")
    public QuizResultDto getResult(@PathVariable UUID sessionId) {
        return quizSessionService.getResult(sessionId);
    }

    // Downloads the JSON blob written by QuizResultExportService when getResult() above was last
    // called for this session (Storage Account, see storage.tf in the infra repo) -- streamed
    // through the backend's own managed identity rather than a SAS, consistent with the account
    // having no access keys (shared_access_key_enabled = false).
    @GetMapping("/{sessionId}/result/export")
    public ResponseEntity<byte[]> exportResult(@PathVariable UUID sessionId) {
        byte[] json = quizResultExportService.download(sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"quiz-result-" + sessionId + ".json\"")
                .body(json);
    }
}
