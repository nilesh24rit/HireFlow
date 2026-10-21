package com.hireflow.ai.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.Duration;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.hireflow.ai.config.AiProperties;
import com.hireflow.ai.exception.InvalidResumeException;

class DocumentResumeTextExtractorTest {

    private final DocumentResumeTextExtractor extractor = new DocumentResumeTextExtractor(
            new AiProperties("key", "test-model", Duration.ofSeconds(5), 100, 0,
                    5 * 1024 * 1024, 1000));

    @Test
    void extractsAndNormalizesPdfText() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            try (var stream = new PDPageContentStream(document, document.getPage(0))) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Ada   Lovelace");
                stream.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        String text = extractor.extract(new MockMultipartFile("file", "resume.pdf",
                "text/plain", pdf));

        assertThat(text).contains("Ada Lovelace");
    }

    @Test
    void extractsDocxTextWithoutTrustingMimeType() throws Exception {
        byte[] docx;
        try (var document = new XWPFDocument();
                var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Grace Hopper");
            document.write(output);
            docx = output.toByteArray();
        }

        String text = extractor.extract(new MockMultipartFile("file", "resume.docx",
                "application/octet-stream", docx));

        assertThat(text).contains("Grace Hopper");
    }

    @Test
    void rejectsEmptyUnsupportedAndMismatchedFiles() {
        assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file", "resume.pdf",
                "application/pdf", new byte[0])))
                .isInstanceOf(InvalidResumeException.class);
        assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file", "resume.exe",
                "application/octet-stream", new byte[] { 1 })))
                .isInstanceOf(InvalidResumeException.class);
        assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file", "resume.pdf",
                "application/pdf", "not a pdf".getBytes())))
                .isInstanceOf(InvalidResumeException.class);
    }

    @Test
    void rejectsOversizedFilesBeforeParsing() {
        DocumentResumeTextExtractor limited = new DocumentResumeTextExtractor(new AiProperties(
                "key", "test-model", Duration.ofSeconds(5), 100, 0, 3, 1000));

        assertThatThrownBy(() -> limited.extract(new MockMultipartFile("file", "resume.pdf",
                "application/pdf", new byte[] { '%', 'P', 'D', 'F' })))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void rejectsScannedPdfWithoutTextLayer() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file", "scan.pdf",
                "application/pdf", pdf)))
                .isInstanceOf(InvalidResumeException.class)
                .hasMessageContaining("extractable text");
    }
}
