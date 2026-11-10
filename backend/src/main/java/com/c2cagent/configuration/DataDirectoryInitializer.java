package com.c2cagent.configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ensures the configurable local data directory (resumes, attachments, backups, logs) exists. */
@Component
public class DataDirectoryInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataDirectoryInitializer.class);
    public static final String[] SUBDIRS = {"resumes", "attachments", "exports", "backups", "logs"};

    private final Path dataDir;

    public DataDirectoryInitializer(AppProperties props) {
        this.dataDir = props.dataDir().toAbsolutePath().normalize();
        try {
            for (String sub : SUBDIRS) {
                Files.createDirectories(dataDir.resolve(sub));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + dataDir, e);
        }
        log.info("Data directory: {}", dataDir);
    }

    public Path dataDir() { return dataDir; }
}
