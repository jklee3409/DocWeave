package com.docweave.server.doc.service.component.parser;

import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.exception.FileHandlingException;
import java.io.File;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikaClient {

    @Value("${docweave.tika.base-url}")
    private String tikaUrl;

    public String parsePdfToXhtml(File file) {
        log.info("Tika Request: File Name={}, Size={} bytes", file.getName(), file.length());

        try {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                    .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                    .build();

            WebClient webClient = WebClient.builder()
                    .baseUrl(tikaUrl)
                    .exchangeStrategies(strategies)
                    .build();

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new FileSystemResource(file));

            String response = webClient.post()
                    .uri("/tika/form")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN)
                    .header("X-Tika-PDFextractInlineImages", "true")
                    .header("X-Tika-OCRtimeout", "300")
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                    .block();

            if (response == null || response.isBlank()) {
                log.error("Tika Response is null or empty");
                throw new FileHandlingException(ErrorCode.FILE_EMPTY);
            }

            log.info("Tika Parsing Success.");
            return response;

        } catch (Exception e) {
            log.error("Tika Parsing Failed", e);
            throw new FileHandlingException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
}