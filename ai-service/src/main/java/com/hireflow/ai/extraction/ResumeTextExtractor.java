package com.hireflow.ai.extraction;

import org.springframework.web.multipart.MultipartFile;

public interface ResumeTextExtractor {

    String extract(MultipartFile file);
}
