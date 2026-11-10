package com.c2cagent.gmail;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailSyncCheckpointRepository extends JpaRepository<GmailSyncCheckpoint, UUID> {}
