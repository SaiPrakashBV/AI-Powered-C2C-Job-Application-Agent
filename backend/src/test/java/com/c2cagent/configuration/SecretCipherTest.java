package com.c2cagent.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretCipherTest {
    @TempDir Path dir;

    private DataDirectoryInitializer dataDir(Path p) {
        return new DataDirectoryInitializer(new AppProperties(p, false, new AppProperties.Security(5, Duration.ofMinutes(1), 12),
                new AppProperties.Jobs(false, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new AppProperties.Email(1000, 1000, 10, 2, 10)));
    }

    @Test
    void roundTripsAndUsesRandomIv() throws Exception {
        SecretCipher c = new SecretCipher(dataDir(dir));
        String a = c.encrypt("1//refresh-token-value");
        String b = c.encrypt("1//refresh-token-value");
        assertThat(a).startsWith("v1:").isNotEqualTo(b).doesNotContain("refresh-token");
        assertThat(c.decrypt(a)).isEqualTo("1//refresh-token-value");
        // Same key file → a new instance (application restart) can still decrypt.
        assertThat(new SecretCipher(dataDir(dir)).decrypt(a)).isEqualTo("1//refresh-token-value");
        assertThat(Files.exists(dir.resolve("keys/secrets.key"))).isTrue();
    }

    @Test
    void tamperedCiphertextIsRejected() {
        SecretCipher c = new SecretCipher(dataDir(dir));
        String enc = c.encrypt("secret");
        String tampered = enc.substring(0, enc.length() - 4) + (enc.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
