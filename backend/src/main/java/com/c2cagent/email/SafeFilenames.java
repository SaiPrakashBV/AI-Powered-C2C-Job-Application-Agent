package com.c2cagent.email;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Produces filenames that are safe to store on Windows/Linux: no paths, reserved names or control chars. */
public final class SafeFilenames {
    private static final Pattern UNSAFE = Pattern.compile("[\\p{Cntrl}<>:\"/\\\\|?*]");
    private static final Set<String> RESERVED = Set.of("con", "prn", "aux", "nul", "com1", "com2", "com3", "com4",
            "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private SafeFilenames() {}

    public static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "attachment.bin";
        }
        String n = Normalizer.normalize(name, Normalizer.Form.NFC);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = UNSAFE.matcher(n).replaceAll("_").strip();
        while (n.startsWith(".")) {
            n = n.substring(1);
        }
        n = n.replaceAll("[. ]+$", "");
        if (n.isEmpty()) {
            return "attachment.bin";
        }
        String base = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (RESERVED.contains(base.toLowerCase(Locale.ROOT))) {
            n = "_" + n;
        }
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 10 ? n.substring(dot) : "";
            n = n.substring(0, 120 - ext.length()) + ext;
        }
        return n;
    }

    public static String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
