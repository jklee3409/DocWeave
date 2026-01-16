package com.docweave.server.doc.controller;

import com.docweave.server.doc.service.RagService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/doc")
public class DocController {

    private final RagService ragService;

    public DocController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            ragService.uploadPdf(file);
            return ResponseEntity.ok(Map.of("message", "문서가 성공적으로 학습되었습니다."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "업로드 실패: " + e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, String> request) {
        String question = request.get("message");
        String answer = ragService.ask(question);

        return ResponseEntity.ok(Map.of(
                "question", question,
                "answer", answer
        ));
    }
}
