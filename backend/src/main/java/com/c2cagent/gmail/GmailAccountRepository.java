package com.c2cagent.gmail;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GmailAccountRepository extends JpaRepository<GmailAccount, UUID> {
    List<GmailAccount> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<GmailAccount> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("select a from GmailAccount a where a.workspaceId = ?1 and lower(a.emailAddress) = lower(?2)")
    Optional<GmailAccount> findByWorkspaceAndEmail(UUID workspaceId, String email);

    List<GmailAccount> findByStatusAndSyncEnabledTrue(GmailAccountStatus status);
}
