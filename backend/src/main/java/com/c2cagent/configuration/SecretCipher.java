package com.c2cagent.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets at rest (OAuth refresh/access tokens, later AI API keys) with AES-256-GCM.
 * The key is generated on first use and stored in {@code <data-dir>/keys/secrets.key}, outside the database, so a
 * database dump/backup alone does not reveal tokens. On Windows the data directory lives in the user profile,
 * which is protected by NTFS ACLs. Format: "v1:" + base64(iv || ciphertext+tag).
 */
@Component
public class SecretCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final SecretKey key;

    public SecretCipher(DataDirectoryInitializer dataDir) {
        this.key = loadOrCreateKey(dataDir.dataDir().resolve("keys").resolve("secrets.key"));
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (!encoded.startsWith(PREFIX)) {
            throw new IllegalStateException("Unknown secret format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Secret could not be decrypted (wrong key or corrupted value)", e);
        }
    }

    private static SecretKey loadOrCreateKey(Path file) {
        try {
            if (Files.exists(file)) {
                return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(file).strip()), "AES");
            }
            Files.createDirectories(file.getParent());
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256);
            SecretKey k = kg.generateKey();
            Files.writeString(file, Base64.getEncoder().encodeToString(k.getEncoded()));
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: protected by the user-profile ACLs of the data directory.
            }
            return k;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read/create secret key " + file, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
