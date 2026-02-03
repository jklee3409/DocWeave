package com.docweave.server.doc.service.component.handler;

import com.docweave.server.common.constant.FileConstant;
import com.docweave.server.common.exception.ErrorCode;
import com.docweave.server.doc.exception.FileHandlingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileHandler {

    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new FileHandlingException(ErrorCode.FILE_EMPTY);

        if (!Objects.requireNonNull(file.getOriginalFilename()).toLowerCase().endsWith(".pdf"))
            throw new FileHandlingException(ErrorCode.INVALID_FILE_EXTENSION);
    }

    public String saveTempFile(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String tempFileName = UUID.randomUUID() + "_" + originalName;

        Path path = Path.of(FileConstant.TEMP_DIR, tempFileName);
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

        return path.toString();
    }
}