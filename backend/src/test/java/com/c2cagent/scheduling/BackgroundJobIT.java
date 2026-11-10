package com.c2cagent.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c2cagent.IntegrationTestBase;
import com.c2cagent.diagnostics.SelfTestJobHandler;
import com.c2cagent.users.AppUser;
import com.c2cagent.users.UserRole;
import com.c2cagent.users.AppUserRepository;
import com.c2cagent.users.Workspace;
import com.c2cagent.users.WorkspaceRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(BackgroundJobIT.TestHandlers.class)
class BackgroundJobIT extends IntegrationTestBase {

    @TestConfiguration
    static class TestHandlers {
        @Bean
        JobHandler flakyHandler() {
            return new JobHandler() {
                public String jobType() { return "TEST_TRANSIENT"; }
                public void handle(BackgroundJob job) { throw JobFailure.transientFailure("upstream timeout", null); }
            };
        }

        @Bean
        JobHandler brokenHandler() {
            return new JobHandler() {
                public String jobType() { return "TEST_PERMANENT"; }
                public void handle(BackgroundJob job) { throw JobFailure.permanent("bad input"); }
            };
        }
    }

    @Autowired BackgroundJobService service;
    @Autowired BackgroundJobRepository repository;
    @Autowired JobWorker worker;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AppUserRepository users;

    @Test
    void enqueueWithDedupKeyIsIdempotent() {
        AppUser owner = createOwner("owner");
        var a = service.enqueue(owner.getWorkspaceId(), "TEST_TRANSIENT", "{\"x\":1}", "msg-1", 3);
        var b = service.enqueue(owner.getWorkspaceId(), "TEST_TRANSIENT", "{\"x\":1}", "msg-1", 3);
        assertThat(b.getId()).isEqualTo(a.getId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void selfTestJobRunsThroughQueueViaApi() throws Exception {
        AppUser owner = createOwner("owner");
        mvc.perform(post("/api/v1/diagnostics/self-test").with(as(owner)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QUEUED"));
        assertThat(worker.runOnce()).isEqualTo(1);
        var job = repository.findAll().getFirst();
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getJobType()).isEqualTo(SelfTestJobHandler.TYPE);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE event_type='DIAGNOSTICS_SELF_TEST_PASSED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void transientFailuresBackOffThenGoDead() {
        AppUser owner = createOwner("owner");
        var job = service.enqueue(owner.getWorkspaceId(), "TEST_TRANSIENT", null, null, 2);
        worker.runOnce();
        var after1 = repository.findById(job.getId()).orElseThrow();
        assertThat(after1.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(after1.getAttempts()).isEqualTo(1);
        assertThat(after1.getRunAfter()).isAfter(Instant.now());
        assertThat(after1.getLastError()).isEqualTo("upstream timeout");
        assertThat(worker.runOnce()).as("not due yet").isZero();

        jdbc.update("UPDATE background_job SET run_after = now() - interval '1 second'");
        worker.runOnce();
        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.DEAD);
    }

    @Test
    void permanentFailuresAndUnknownTypesFailImmediatelyAndCanBeRetried() {
        AppUser owner = createOwner("owner");
        var bad = service.enqueue(owner.getWorkspaceId(), "TEST_PERMANENT", null, null, 5);
        var unknown = service.enqueue(owner.getWorkspaceId(), "NO_SUCH_TYPE", null, null, 5);
        worker.runOnce();
        assertThat(repository.findById(bad.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(repository.findById(unknown.getId()).orElseThrow().getLastError()).contains("No handler");

        var retried = service.retry(owner.getWorkspaceId(), bad.getId());
        assertThat(retried.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(retried.getAttempts()).isZero();
    }

    @Test
    void orphanedRunningJobsAreRecoveredAfterRestart() {
        AppUser owner = createOwner("owner");
        var job = service.enqueue(owner.getWorkspaceId(), SelfTestJobHandler.TYPE, null, null, 3);
        service.claim("worker-from-previous-process", 10);
        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);

        assertThat(service.recoverOrphaned(worker.workerId())).isEqualTo(1);
        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.QUEUED);
        worker.runOnce();
        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void jobsAreIsolatedBetweenWorkspaces() throws Exception {
        AppUser owner = createOwner("owner");
        Workspace other = workspaces.save(new Workspace("other"));
        AppUser stranger = users.save(new AppUser(other.getId(), "stranger", "Stranger", null, "{noop}x",
                UserRole.OWNER));
        var foreignJob = service.enqueue(other.getId(), "TEST_PERMANENT", null, null, 1);
        worker.runOnce();

        mvc.perform(get("/api/v1/jobs").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/jobs").with(as(stranger))).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(post("/api/v1/jobs/" + foreignJob.getId() + "/retry").with(as(owner)).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(repository.findById(foreignJob.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.FAILED);
    }
}
