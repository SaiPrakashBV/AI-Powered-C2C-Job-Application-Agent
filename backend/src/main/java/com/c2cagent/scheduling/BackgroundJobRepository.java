package com.c2cagent.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {
    Page<BackgroundJob> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<BackgroundJob> findByWorkspaceIdAndStatusIn(UUID workspaceId, List<JobStatus> statuses, Pageable pageable);

    @Query("select j.status as status, count(j) as count from BackgroundJob j where j.workspaceId = ?1 group by j.status")
    List<StatusCount> countByStatus(UUID workspaceId);

    interface StatusCount {
        JobStatus getStatus();
        long getCount();
    }
}
