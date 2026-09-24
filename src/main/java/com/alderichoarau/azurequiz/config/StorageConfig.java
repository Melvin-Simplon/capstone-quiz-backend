package com.alderichoarau.azurequiz.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    @Value("${app.storage.container-name}")
    private String containerName;

    @Bean
    public BlobContainerClient resultsContainerClient(BlobServiceClient blobServiceClient) {
        BlobContainerClient client = blobServiceClient.getBlobContainerClient(containerName);
        client.createIfNotExists();
        return client;
    }
}
