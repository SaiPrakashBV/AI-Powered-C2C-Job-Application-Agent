package com.c2cagent.gmail;

import com.c2cagent.scheduling.BackgroundJob;
import com.c2cagent.scheduling.JobFailure;
import com.c2cagent.scheduling.JobHandler;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GmailSyncJobHandler implements JobHandler {
    public static final String TYPE = "GMAIL_SYNC";
    private final GmailSyncService sync;

    public GmailSyncJobHandler(GmailSyncService sync) { this.sync = sync; }

    @Override
    public String jobType() { return TYPE; }

    @Override
    public void handle(BackgroundJob job) {
        String payload = job.getPayload();
        if (payload == null || !payload.contains("accountId")) throw JobFailure.permanent("Missing accountId");
        String id = payload.replaceAll(".*\"accountId\"\\s*:\\s*\"([0-9a-fA-F-]{36})\".*", "$1");
        sync.sync(UUID.fromString(id));
    }

    public static String payload(UUID accountId) { return "{\"accountId\":\"" + accountId + "\"}"; }

    public static String dedupKey(UUID accountId) { return "gmail-sync:" + accountId; }
}
