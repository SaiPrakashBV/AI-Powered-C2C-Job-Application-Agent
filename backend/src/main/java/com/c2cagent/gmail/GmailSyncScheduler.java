package com.c2cagent.gmail;

import com.c2cagent.scheduling.BackgroundJobService;
import com.c2cagent.users.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic polling: enqueues a GMAIL_SYNC job per connected account when its polling interval has elapsed. */
@Component
public class GmailSyncScheduler {
    private final GmailAccountRepository accounts;
    private final SettingsService settings;
    private final BackgroundJobService jobs;
    private final GmailProperties props;
    private final Clock clock;

    public GmailSyncScheduler(GmailAccountRepository accounts, SettingsService settings, BackgroundJobService jobs,
                              GmailProperties props, Optional<Clock> clock) {
        this.accounts = accounts;
        this.settings = settings;
        this.jobs = jobs;
        this.props = props;
        this.clock = clock.orElse(Clock.systemUTC());
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT20S")
    public void tick() {
        if (props.schedulerEnabled()) enqueueDue();
    }

    public int enqueueDue() {
        int n = 0;
        Instant now = clock.instant();
        for (GmailAccount a : accounts.findByStatusAndSyncEnabledTrue(GmailAccountStatus.CONNECTED)) {
            int minutes = settings.getOrCreate(a.getWorkspaceId()).getGmailPollingIntervalMinutes();
            if (a.getLastSyncAt() == null || a.getLastSyncAt().isBefore(now.minus(Duration.ofMinutes(minutes)))) {
                jobs.enqueue(a.getWorkspaceId(), GmailSyncJobHandler.TYPE, GmailSyncJobHandler.payload(a.getId()),
                        GmailSyncJobHandler.dedupKey(a.getId()), 5);
                n++;
            }
        }
        return n;
    }
}
