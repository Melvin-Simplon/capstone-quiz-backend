package com.alderichoarau.azurequiz.service;

import com.alderichoarau.azurequiz.dto.QuizResultDto;
import com.alderichoarau.azurequiz.exception.ResourceNotFoundException;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizResultExportService {
    private final BlobContainerClient resultsContainerClient;
    private final ObjectMapper objectMapper;

    public void export(QuizResultDto result) {
        String blobName = blobName(result.sessionId());
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
            resultsContainerClient
                    .getBlobClient(blobName)
                    .upload(new ByteArrayInputStream(json), json.length, true);
            log.info("Exported result for session {} to blob '{}'", result.sessionId(), blobName);
        } catch (Exception e) {
            log.warn(
                    "Failed to export result for session {} to blob storage (non-fatal): {}",
                    result.sessionId(),
                    e.getMessage());
        }
    }

    public byte[] download(UUID sessionId) {
        String blobName = blobName(sessionId);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            resultsContainerClient.getBlobClient(blobName).downloadStream(out);
            return out.toByteArray();
        } catch (BlobStorageException e) {
            throw new ResourceNotFoundException("No exported result found for session: " + sessionId);
        }
    }

    private String blobName(UUID sessionId) {
        return "results/" + sessionId + ".json";
    }
}
