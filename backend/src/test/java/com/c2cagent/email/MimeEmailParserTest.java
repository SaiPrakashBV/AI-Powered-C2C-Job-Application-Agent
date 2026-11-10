package com.c2cagent.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.c2cagent.configuration.AppProperties;
import com.c2cagent.fixtures.TestEmails;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MimeEmailParserTest {
    static final AppProperties PROPS = new AppProperties(Path.of("."), false,
            new AppProperties.Security(5, Duration.ofMinutes(15), 12),
            new AppProperties.Jobs(true, 5, Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(30)),
            new AppProperties.Email(50_000, 500_000, 200, 8, 5000));
    final MimeEmailParser parser = new MimeEmailParser(PROPS);
    final AttachmentTextExtractor extractor = new AttachmentTextExtractor(PROPS);

    @Test
    void scenario1_jdWithNoSubjectKeepsBodyAndNullSubject() throws Exception {
        ParsedEmail p = parser.parse(TestEmails.message().text(TestEmails.JAVA_JD).bytes());
        assertThat(p.subject()).isNull();
        assertThat(p.fromAddress()).isEqualTo("alex@vendor-a.example");
        assertThat(p.fromName()).isEqualTo("Alex Recruiter");
        assertThat(p.textBody()).contains("Senior Java Developer").contains("Spring Boot");
        assertThat(p.warnings()).isEmpty();
    }

    @Test
    void scenario17_htmlOnlyMessageIsSanitizedAndConvertedToText() throws Exception {
        String html = "<html><body><p>Role: <b>Java Backend Developer</b></p><script>alert('x')</script>"
                + "<img src='http://tracker.example/p.gif'><ul><li>Spring Boot</li><li>AWS</li></ul>"
                + "<a href='javascript:evil()'>apply</a></body></html>";
        ParsedEmail p = parser.parse(TestEmails.message().subject("Req").html(html).bytes());
        assertThat(p.textBody()).isNull();
        assertThat(p.htmlSanitized()).doesNotContain("<script").doesNotContain("<img").doesNotContain("javascript:");
        assertThat(p.htmlText()).contains("Role: Java Backend Developer").contains("• Spring Boot").contains("• AWS");
    }

    @Test
    void quotedPrintableAndLatin1CharsetAreDecoded() throws Exception {
        String body = "Location: Montréal – C2C ok. Résumé needed.";
        ParsedEmail p = parser.parse(TestEmails.message().text(body).charset("ISO-8859-1", "quoted-printable").bytes());
        assertThat(p.textBody()).contains("Montréal").contains("Résumé");
    }

    @Test
    void encodedSubjectIsDecoded() throws Exception {
        ParsedEmail p = parser.parse(TestEmails.message().subject("Urgent – Java Développeur").text("x").bytes());
        assertThat(p.subject()).isEqualTo("Urgent – Java Développeur");
    }

    @Test
    void scenario4_pdfAttachmentTextIsExtracted() throws Exception {
        byte[] pdf = TestEmails.pdf("Job Description\nSenior Java Developer with Spring Boot and AWS");
        ParsedEmail p = parser.parse(TestEmails.message().text("Please see attached JD").attach("JD.pdf", "application/pdf", pdf).bytes());
        assertThat(p.attachments()).hasSize(1);
        var r = extractor.extract(p.attachments().getFirst());
        assertThat(r.status()).isEqualTo(AttachmentExtractionStatus.EXTRACTED);
        assertThat(r.text()).contains("Senior Java Developer");
    }

    @Test
    void scenario5_docxAttachmentTextIsExtracted() throws Exception {
        byte[] docx = TestEmails.docx("Requirement: Java Full Stack Developer\nReact, Spring Boot, Microservices");
        ParsedEmail p = parser.parse(TestEmails.message().attach("req.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx).bytes());
        var r = extractor.extract(p.attachments().getFirst());
        assertThat(r.status()).isEqualTo(AttachmentExtractionStatus.EXTRACTED);
        assertThat(r.text()).contains("Java Full Stack Developer");
    }

    @Test
    void scenario16_malformedAttachmentIsFlaggedNotSilentlyDropped() throws Exception {
        ParsedEmail p = parser.parse(TestEmails.message().text("JD attached")
                .attach("broken.pdf", "application/pdf", "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.US_ASCII))
                .attach("legacy.doc", "application/msword", new byte[] {1, 2, 3})
                .attach("logo.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}).bytes());
        assertThat(extractor.extract(p.attachments().get(0)).status()).isEqualTo(AttachmentExtractionStatus.FAILED);
        assertThat(extractor.extract(p.attachments().get(1)).status()).isEqualTo(AttachmentExtractionStatus.NEEDS_REVIEW);
        assertThat(extractor.extract(p.attachments().get(2)).status()).isEqualTo(AttachmentExtractionStatus.UNSUPPORTED);
    }

    @Test
    void oversizedAttachmentIsRecordedAsTooLargeWithoutContent() throws Exception {
        ParsedEmail p = parser.parse(TestEmails.message().text("big").attach("huge.txt", "text/plain", new byte[60_000]).bytes());
        var a = p.attachments().getFirst();
        assertThat(a.tooLarge()).isTrue();
        assertThat(a.content()).isNull();
        assertThat(extractor.extract(a).status()).isEqualTo(AttachmentExtractionStatus.TOO_LARGE);
        assertThat(p.warnings()).anyMatch(w -> w.contains("size limit"));
    }

    @Test
    void unsafeAttachmentFilenamesAreSanitized() throws Exception {
        ParsedEmail p = parser.parse(TestEmails.message().text("x").attach("../../Windows/evil:name?.txt", "text/plain",
                "hello".getBytes()).bytes());
        assertThat(p.attachments().getFirst().safeFilename()).isEqualTo("evil_name_.txt");
        assertThat(SafeFilenames.sanitize("CON.txt")).isEqualTo("_CON.txt");
        assertThat(SafeFilenames.sanitize("..")).isEqualTo("attachment.bin");
    }

    @Test
    void scenario7_forwardedMessageContentIsKeptWithOriginalHeaders() throws Exception {
        var inner = TestEmails.message().from("Original Recruiter <orig@vendor-c.example>").subject("Java Developer - Austin")
                .text("Need a Java Developer with Spring Boot. Contact orig@vendor-c.example").build();
        ParsedEmail p = parser.parse(TestEmails.message().from("Friend <friend@example.com>").subject("Fwd: Java Developer - Austin")
                .text("FYI see below").forward(inner).bytes());
        assertThat(p.fromAddress()).isEqualTo("friend@example.com");
        assertThat(p.textBody()).contains("FYI see below").contains("Forwarded message")
                .contains("orig@vendor-c.example").contains("Need a Java Developer");
    }

    @Test
    void garbageInputDoesNotThrowAndYieldsNoBody() throws Exception {
        ParsedEmail p = parser.parse("this is not an email at all".getBytes());
        assertThat(p.fromAddress()).isNull();
        assertThat(p.subject()).isNull();
    }
}
