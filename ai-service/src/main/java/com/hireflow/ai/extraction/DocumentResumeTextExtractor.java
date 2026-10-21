package com.hireflow.ai.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.hireflow.ai.config.AiProperties;
import com.hireflow.ai.exception.InvalidResumeException;

@Component
public class DocumentResumeTextExtractor implements ResumeTextExtractor {

    private static final String PDF_EXTENSION = "pdf";
    private static final String DOCX_EXTENSION = "docx";

    private final AiProperties properties;

    public DocumentResumeTextExtractor(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidResumeException("Resume file is required and must not be empty");
        }
        if (file.getSize() > properties.maxResumeBytes()) {
            throw new InvalidResumeException("Resume file exceeds the configured size limit");
        }
        String extension = extension(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidResumeException("Resume file could not be read", ex);
        }
        if (PDF_EXTENSION.equals(extension)) {
            requirePdf(content);
            return normalize(extractPdf(content));
        }
        if (DOCX_EXTENSION.equals(extension)) {
            requireDocx(content);
            return normalize(extractDocx(content));
        }
        throw new InvalidResumeException("Only PDF and DOCX resumes are supported");
    }

    private String extractPdf(byte[] content) {
        try (var document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException ex) {
            throw new InvalidResumeException("Resume PDF is unreadable", ex);
        }
    }

    private String extractDocx(byte[] content) {
        try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (!paragraph.getText().isBlank()) {
                    text.append(paragraph.getText()).append('\n');
                }
            }
            return text.toString();
        } catch (IOException | RuntimeException ex) {
            throw new InvalidResumeException("Resume DOCX is unreadable", ex);
        }
    }

    private String normalize(String text) {
        String normalized = text == null ? "" : text
                .replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *\\n *\\n+", "\n\n")
                .trim();
        if (normalized.isBlank()) {
            throw new InvalidResumeException("Resume does not contain an extractable text layer");
        }
        if (normalized.length() > properties.maxExtractedCharacters()) {
            throw new InvalidResumeException("Resume text exceeds the configured processing limit");
        }
        return normalized;
    }

    private void requirePdf(byte[] content) {
        if (content.length < 5 || content[0] != '%' || content[1] != 'P'
                || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new InvalidResumeException("Resume content does not match the PDF format");
        }
    }

    private void requireDocx(byte[] content) {
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K'
                || content[2] != 3 || content[3] != 4 || !containsDocumentEntry(content)) {
            throw new InvalidResumeException("Resume content does not match the DOCX format");
        }
    }

    private boolean containsDocumentEntry(byte[] content) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException ex) {
            throw new InvalidResumeException("Resume DOCX is unreadable", ex);
        }
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
