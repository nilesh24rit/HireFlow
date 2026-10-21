package com.hireflow.ai.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ai/resumes")
@Tag(name = "Resume processing")
public class ResumeController {

    private final ResumeProcessingUseCase useCase;

    public ResumeController(ResumeProcessingUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(path = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract structured information from a resume")
    public ResumeProcessingResponse process(@RequestPart("file") MultipartFile file) {
        return useCase.process(file);
    }
}
