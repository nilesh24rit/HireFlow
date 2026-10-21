package com.hireflow.ai.api;

import org.springframework.web.multipart.MultipartFile;

public interface ResumeProcessingUseCase {

    ResumeProcessingResponse process(MultipartFile file);
}
