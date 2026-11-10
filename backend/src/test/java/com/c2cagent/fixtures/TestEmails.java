package com.c2cagent.fixtures;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Builds anonymized RFC 822 test messages (no real recruiter data). */
public final class TestEmails {
    private TestEmails() {}

    public static final String JAVA_JD = """
            Hi,
            Please find the requirement below and share your updated resume.
            Role: Senior Java Developer
            Location: Dallas, TX (Hybrid)  Duration: 12 months  C2C OK
            Must have: Java 17, Spring Boot, Microservices, AWS, Kafka
            Thanks,
            Alex Recruiter | Vendor A Staffing | alex@vendor-a.example""";

    public static Builder message() { return new Builder(); }

    public static final class Builder {
        private String from = "Alex Recruiter <alex@vendor-a.example>";
        private String to = "candidate@example.com";
        private String subject;
        private String text;
        private String html;
        private String charset = "UTF-8";
        private String transferEncoding;
        private final List<MimeBodyPart> attachments = new ArrayList<>();
        private MimeMessage forwarded;

        public Builder from(String f) { from = f; return this; }
        public Builder subject(String s) { subject = s; return this; }
        public Builder text(String t) { text = t; return this; }
        public Builder html(String h) { html = h; return this; }
        public Builder charset(String c, String encoding) { charset = c; transferEncoding = encoding; return this; }

        public Builder attach(String filename, String contentType, byte[] data) {
            try {
                MimeBodyPart p = new MimeBodyPart();
                p.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(data, contentType)));
                p.setFileName(filename);
                p.setDisposition(jakarta.mail.Part.ATTACHMENT);
                p.setHeader("Content-Transfer-Encoding", "base64");
                attachments.add(p);
                return this;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public Builder forward(MimeMessage inner) { forwarded = inner; return this; }

        public MimeMessage build() {
            try {
                MimeMessage m = new MimeMessage(Session.getInstance(new Properties()));
                m.setFrom(new InternetAddress(from));
                m.setRecipients(Message.RecipientType.TO, to);
                if (subject != null) m.setSubject(subject, "UTF-8");
                m.setSentDate(new Date(1_762_000_000_000L));
                MimeMultipart mixed = new MimeMultipart("mixed");
                if (text != null && html != null) {
                    MimeMultipart alt = new MimeMultipart("alternative");
                    alt.addBodyPart(textPart(text));
                    MimeBodyPart hp = new MimeBodyPart();
                    hp.setText(html, "UTF-8", "html");
                    alt.addBodyPart(hp);
                    MimeBodyPart altWrap = new MimeBodyPart();
                    altWrap.setContent(alt);
                    mixed.addBodyPart(altWrap);
                } else if (html != null) {
                    MimeBodyPart hp = new MimeBodyPart();
                    hp.setText(html, "UTF-8", "html");
                    mixed.addBodyPart(hp);
                } else if (text != null) {
                    mixed.addBodyPart(textPart(text));
                }
                if (forwarded != null) {
                    MimeBodyPart fp = new MimeBodyPart();
                    fp.setContent(forwarded, "message/rfc822");
                    mixed.addBodyPart(fp);
                }
                for (MimeBodyPart a : attachments) mixed.addBodyPart(a);
                m.setContent(mixed);
                m.saveChanges();
                return m;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private MimeBodyPart textPart(String t) throws Exception {
            MimeBodyPart tp = new MimeBodyPart();
            tp.setText(t, charset);
            if (transferEncoding != null) tp.setHeader("Content-Transfer-Encoding", transferEncoding);
            return tp;
        }

        public byte[] bytes() {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                build().writeTo(out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static byte[] pdf(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(50, 700);
                for (String line : text.split("\n")) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -14);
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] docx(String text) {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String line : text.split("\n")) doc.createParagraph().createRun().setText(line);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
