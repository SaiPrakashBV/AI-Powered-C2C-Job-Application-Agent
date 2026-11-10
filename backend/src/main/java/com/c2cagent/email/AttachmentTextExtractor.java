package com.c2cagent.email;

import com.c2cagent.configuration.AppProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * Extracts text from PDF, DOCX and TXT attachments. Unknown formats that could contain a JD (doc, rtf, odt) are
 * flagged NEEDS_REVIEW instead of being ignored; images and other binaries are UNSUPPORTED. Failures are
 * reported, never swallowed. DOCX zip-bomb protection uses POI's ZipSecureFile limits.
 */
@Component
public class AttachmentTextExtractor {
    private static final Set<String> REVIEW_TYPES = Set.of("doc", "rtf", "odt", "pages", "wps");
    private final AppProperties.Email limits;

    public record Result(AttachmentExtractionStatus status, String text, String error) {}

    public AttachmentTextExtractor(AppProperties props) {
        this.limits = props.email();
        ZipSecureFile.setMinInflateRatio(0.01);              // reject highly compressed (zip bomb) entries
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    public Result extract(ParsedEmail.Attachment a) {
        if (a.tooLarge()) {
            return new Result(AttachmentExtractionStatus.TOO_LARGE, null, a.error());
        }
        if (a.content() == null) {
            return new Result(AttachmentExtractionStatus.FAILED, null, a.error());
        }
        String ext = SafeFilenames.extension(a.safeFilename());
        String type = a.contentType() == null ? "" : a.contentType();
        byte[] data = a.content();
        try {
            if (ext.equals("pdf") || type.equals("application/pdf") || startsWith(data, "%PDF")) {
                return pdf(data);
            }
            if (ext.equals("docx") || type.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return docx(data);
            }
            if (ext.equals("txt") || type.equals("text/plain")) {
                return text(new String(data, StandardCharsets.UTF_8));
            }
        } catch (InvalidPasswordException e) {
            return new Result(AttachmentExtractionStatus.NEEDS_REVIEW, null, "Password-protected document");
        } catch (Exception | OutOfMemoryError e) {
            return new Result(AttachmentExtractionStatus.FAILED, null,
                    "Text extraction failed (" + e.getClass().getSimpleName() + "); flagged for manual review");
        }
        if (REVIEW_TYPES.contains(ext) || type.equals("application/msword")) {
            return new Result(AttachmentExtractionStatus.NEEDS_REVIEW, null,
                    "Format ." + ext + " is not supported for automatic extraction; please review manually");
        }
        return new Result(AttachmentExtractionStatus.UNSUPPORTED, null, null);
    }

    private Result pdf(byte[] data) throws Exception {
        try (PDDocument doc = Loader.loadPDF(data)) {
            if (doc.getNumberOfPages() > limits.maxPdfPages()) {
                return new Result(AttachmentExtractionStatus.NEEDS_REVIEW, null, "PDF has too many pages");
            }
            String t = new PDFTextStripper().getText(doc);
            if (t == null || t.strip().length() < 20) {
                return new Result(AttachmentExtractionStatus.NEEDS_REVIEW, null,
                        "PDF has no extractable text (possibly scanned); OCR is not supported");
            }
            return text(t);
        }
    }

    private Result docx(byte[] data) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return text(ex.getText());
        }
    }

    private Result text(String t) {
        String s = t.replace("\u0000", "").strip();
        if (s.length() > limits.maxTextChars()) {
            s = s.substring(0, limits.maxTextChars());
        }
        return s.isEmpty() ? new Result(AttachmentExtractionStatus.NEEDS_REVIEW, null, "Attachment contains no text")
                : new Result(AttachmentExtractionStatus.EXTRACTED, s, null);
    }

    private static boolean startsWith(byte[] data, String magic) {
        if (data.length < magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != magic.charAt(i)) return false;
        }
        return true;
    }
}
