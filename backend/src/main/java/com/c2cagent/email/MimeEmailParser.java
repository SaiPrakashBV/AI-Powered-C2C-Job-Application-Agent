package com.c2cagent.email;

import com.c2cagent.configuration.AppProperties;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Parses raw RFC 822 messages (Gmail "raw" format) with Jakarta Mail. Handles multipart trees, quoted-printable
 * and Base64, charsets, HTML-only messages, forwarded messages attached as message/rfc822 and attachments.
 * Content is untrusted: HTML is sanitized, sizes, part counts and nesting depth are limited, and problems are
 * recorded as warnings instead of being silently dropped.
 */
@Component
public class MimeEmailParser {
    private static final Safelist HTML_SAFELIST = Safelist.relaxed().removeTags("img");
    private final AppProperties.Email limits;
    private final Session session;

    public MimeEmailParser(AppProperties props) {
        this.limits = props.email();
        Properties p = new Properties();
        p.setProperty("mail.mime.address.strict", "false");
        p.setProperty("mail.mime.decodetext.strict", "false");
        p.setProperty("mail.mime.allowutf8", "true");
        p.setProperty("mail.mime.decodefilename", "true");
        p.setProperty("mail.mime.decodeparameters", "true");
        p.setProperty("mail.mime.parameters.strict", "false");
        p.setProperty("mail.mime.base64.ignoreerrors", "true");
        this.session = Session.getInstance(p);
    }

    public ParsedEmail parse(byte[] raw) throws MessagingException {
        MimeMessage msg = new MimeMessage(session, new ByteArrayInputStream(raw));
        Acc acc = new Acc();
        try {
            walk(msg, acc, 0);
        } catch (MessagingException | IOException | RuntimeException e) {
            acc.warnings.add("Body could not be fully parsed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        InternetAddress from = firstAddress(safe(() -> msg.getFrom()), acc);
        String html = acc.html.isEmpty() ? null : String.join("\n<hr>\n", acc.html);
        String sanitized = html == null ? null : Jsoup.clean(html, HTML_SAFELIST);
        String text = acc.text.isEmpty() ? null : truncate(String.join("\n\n", acc.text), acc);
        return new ParsedEmail(
                from == null ? null : from.getAddress(),
                from == null ? null : from.getPersonal(),
                join(safe(msg::getReplyTo)),
                join(safe(() -> msg.getRecipients(Message.RecipientType.TO))),
                join(safe(() -> msg.getRecipients(Message.RecipientType.CC))),
                cleanSubject(safe(msg::getSubject)),
                header(msg, "Message-ID"),
                header(msg, "In-Reply-To"),
                header(msg, "References"),
                safe(() -> msg.getSentDate() == null ? null : msg.getSentDate().toInstant()),
                text,
                sanitized == null ? null : truncate(sanitized, acc),
                sanitized == null ? null : truncate(htmlToText(sanitized), acc),
                acc.attachments, acc.warnings);
    }

    private static final class Acc {
        final List<String> text = new ArrayList<>();
        final List<String> html = new ArrayList<>();
        final List<ParsedEmail.Attachment> attachments = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int parts;
    }

    private void walk(Part part, Acc acc, int depth) throws MessagingException, IOException {
        if (++acc.parts > limits.maxMimeParts()) {
            if (acc.parts == limits.maxMimeParts() + 1) {
                acc.warnings.add("Message has more than " + limits.maxMimeParts() + " MIME parts; the rest was ignored");
            }
            return;
        }
        if (depth > limits.maxMimeDepth()) {
            acc.warnings.add("MIME nesting deeper than " + limits.maxMimeDepth() + " levels was ignored");
            return;
        }
        String type = lower(part.getContentType());
        String disposition = lower(safe(part::getDisposition));
        String filename = decodeFilename(safe(part::getFileName));
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || filename != null;

        if (type.startsWith("multipart/")) {
            Object content = part.getContent();
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    try {
                        walk(bp, acc, depth + 1);
                    } catch (MessagingException | IOException | RuntimeException e) {
                        acc.warnings.add("Skipped unreadable MIME part " + (i + 1) + ": " + e.getMessage());
                    }
                }
            }
            return;
        }
        if (type.startsWith("message/rfc822")) {
            // Forwarded email attached as a message: keep its headers + text as forwarded content.
            Object content = part.getContent();
            if (content instanceof MimeMessage inner) {
                StringBuilder hdr = new StringBuilder("---------- Forwarded message ----------\n");
                InternetAddress f = firstAddress(safe(() -> inner.getFrom()), acc);
                if (f != null) hdr.append("From: ").append(f.toUnicodeString()).append('\n');
                String subj = safe(inner::getSubject);
                if (subj != null) hdr.append("Subject: ").append(subj).append('\n');
                acc.text.add(hdr.toString().strip());
                walk(inner, acc, depth + 1);
            }
            return;
        }
        if (!isAttachment && type.startsWith("text/plain")) {
            acc.text.add(readText(part, type, acc));
            return;
        }
        if (!isAttachment && type.startsWith("text/html")) {
            acc.html.add(readText(part, type, acc));
            return;
        }
        addAttachment(part, type, filename, Part.INLINE.equalsIgnoreCase(disposition) && type.startsWith("image/"), acc);
    }

    private void addAttachment(Part part, String type, String filename, boolean inline, Acc acc)
            throws MessagingException {
        String mime = type.contains(";") ? type.substring(0, type.indexOf(';')).strip() : type.strip();
        String safeName = SafeFilenames.sanitize(filename);
        try (InputStream in = part.getInputStream()) {
            byte[] buf = in.readNBytes(limits.maxAttachmentBytes() + 1);
            if (buf.length > limits.maxAttachmentBytes()) {
                long total = buf.length + in.transferTo(java.io.OutputStream.nullOutputStream());
                acc.attachments.add(new ParsedEmail.Attachment(filename, safeName, mime, (int) Math.min(total, Integer.MAX_VALUE),
                        null, inline, true, "Larger than the " + limits.maxAttachmentBytes() + "-byte limit"));
                acc.warnings.add("Attachment " + safeName + " exceeds the size limit and was not stored");
                return;
            }
            acc.attachments.add(new ParsedEmail.Attachment(filename, safeName, mime, buf.length, buf, inline, false, null));
        } catch (IOException e) {
            acc.attachments.add(new ParsedEmail.Attachment(filename, safeName, mime, 0, null, inline, false,
                    "Could not decode attachment: " + e.getMessage()));
            acc.warnings.add("Attachment " + safeName + " could not be decoded");
        }
    }

    private String readText(Part part, String contentType, Acc acc) throws MessagingException, IOException {
        try (InputStream in = part.getInputStream()) {
            byte[] bytes = in.readNBytes(limits.maxTextChars() * 4);
            return new String(bytes, charsetOf(contentType, acc));
        }
    }

    private static Charset charsetOf(String contentType, Acc acc) {
        int i = contentType.indexOf("charset=");
        if (i < 0) {
            return StandardCharsets.UTF_8;
        }
        String cs = contentType.substring(i + 8).split("[;\\s]")[0].replace("\"", "").replace("'", "");
        try {
            return Charset.forName(MimeUtility.javaCharset(cs));
        } catch (RuntimeException e) {
            acc.warnings.add("Unknown charset '" + cs + "', decoded as UTF-8");
            return StandardCharsets.UTF_8;
        }
    }

    /** Converts sanitized HTML to readable plain text, keeping line breaks for block elements. */
    static String htmlToText(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Element br : doc.select("br")) {
            br.after("\n");
        }
        for (Element li : doc.select("li")) {
            li.prepend("• ");
        }
        for (Element block : doc.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, table, ul, ol, blockquote, hr")) {
            block.prepend("\n");
            block.append("\n");
        }
        for (Element td : doc.select("td, th")) {
            td.append("\t");
        }
        return doc.wholeText().replace('\u00a0', ' ').replaceAll("[ \\t]+\n", "\n").replaceAll("\n[ \\t]+", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    private String truncate(String s, Acc acc) {
        if (s.length() <= limits.maxTextChars()) {
            return s;
        }
        acc.warnings.add("Body truncated to " + limits.maxTextChars() + " characters");
        return s.substring(0, limits.maxTextChars());
    }

    private static String cleanSubject(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\r\\n\\t]+", " ").strip();
        return t.isEmpty() ? null : (t.length() > 2000 ? t.substring(0, 2000) : t);
    }

    private static String decodeFilename(String f) {
        if (f == null || f.isBlank()) return null;
        try {
            return MimeUtility.decodeText(f);
        } catch (UnsupportedEncodingException e) {
            return f;
        }
    }

    private static InternetAddress firstAddress(Address[] addrs, Acc acc) {
        if (addrs == null) return null;
        for (Address a : addrs) {
            if (a instanceof InternetAddress ia && ia.getAddress() != null) return ia;
        }
        return null;
    }

    private static String join(Address[] addrs) {
        if (addrs == null || addrs.length == 0) return null;
        return Arrays.stream(addrs).map(a -> a instanceof InternetAddress ia ? ia.toUnicodeString() : a.toString())
                .collect(Collectors.joining(", "));
    }

    private static String header(MimeMessage m, String name) {
        String v = safe(() -> m.getHeader(name, " "));
        return v == null ? null : v.replaceAll("[\\r\\n]+", " ").strip();
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private static <T> T safe(ThrowingSupplier<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return null;
        }
    }
}
