package com.docweave.server.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingModelConfig {

    @Bean
    @Primary
    public EmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
       var options = OllamaEmbeddingOptions.builder()
               .model("bge-m3")
                .build();

       var managementOptions = ModelManagementOptions.defaults();

       return new OllamaEmbeddingModel(ollamaApi, options, ObservationRegistry.NOOP, managementOptions);
    }
}
