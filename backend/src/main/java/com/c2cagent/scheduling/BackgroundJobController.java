package com.c2cagent.scheduling;

import com.c2cagent.auth.CurrentUser;
import com.c2cagent.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Background jobs")
public class BackgroundJobController {
    private final BackgroundJobRepository repository;
    private final BackgroundJobService service;

    public BackgroundJobController(BackgroundJobRepository repository, BackgroundJobService service) {
        this.repository = repository;
        this.service = service;
    }

    public record JobDto(UUID id, String jobType, JobStatus status, int attempts, int maxAttempts, Instant runAfter,
                         String lastError, String failureKind, Instant createdAt, Instant completedAt) {
        static JobDto from(BackgroundJob j) {
            return new JobDto(j.getId(), j.getJobType(), j.getStatus(), j.getAttempts(), j.getMaxAttempts(),
                    j.getRunAfter(), j.getLastError(), j.getFailureKind(), j.getCreatedAt(), j.getCompletedAt());
        }
    }

    @GetMapping
    public PageResponse<JobDto> list(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "25") int size,
                                     @RequestParam(required = false) List<JobStatus> status) {
        var pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null || status.isEmpty()
                ? repository.findByWorkspaceId(user.workspaceId(), pr)
                : repository.findByWorkspaceIdAndStatusIn(user.workspaceId(), status, pr);
        return PageResponse.of(result, JobDto::from);
    }

    @PostMapping("/{id}/retry")
    public JobDto retry(CurrentUser user, @PathVariable UUID id) {
        return JobDto.from(service.retry(user.workspaceId(), id));
    }

    @PostMapping("/{id}/cancel")
    public JobDto cancel(CurrentUser user, @PathVariable UUID id) {
        return JobDto.from(service.cancel(user.workspaceId(), id));
    }

    @GetMapping("/statuses")
    public List<JobStatus> statuses() {
        return Arrays.asList(JobStatus.values());
    }
}
