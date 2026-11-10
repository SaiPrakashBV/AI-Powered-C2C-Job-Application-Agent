package com.c2cagent.email;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailSourceRuleRepository extends JpaRepository<EmailSourceRule, UUID> {
    List<EmailSourceRule> findByWorkspaceIdOrderByPriorityAscCreatedAtAsc(UUID workspaceId);

    List<EmailSourceRule> findByWorkspaceIdAndEnabledTrue(UUID workspaceId);

    Optional<EmailSourceRule> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
