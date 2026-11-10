package com.c2cagent.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    @Query("select u from AppUser u where lower(u.username) = lower(?1)")
    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
