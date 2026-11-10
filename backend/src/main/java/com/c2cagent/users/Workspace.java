package com.c2cagent.users;

import com.c2cagent.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Ownership boundary for all user data. One local workspace initially; many in a future hosted version. */
@Entity
@Table(name = "workspace")
public class Workspace extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String name;

    protected Workspace() {}

    public Workspace(String name) { this.name = name; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
