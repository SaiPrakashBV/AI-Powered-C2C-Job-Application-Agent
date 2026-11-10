package com.c2cagent.email;

public enum SourceRuleType {
    ALLOW_ADDRESS, ALLOW_DOMAIN, BLOCK_ADDRESS, BLOCK_DOMAIN, TRUSTED_FORWARDER, ALLOW_LABEL, BLOCK_LABEL;

    public boolean isAddress() { return this == ALLOW_ADDRESS || this == BLOCK_ADDRESS || this == TRUSTED_FORWARDER; }
    public boolean isDomain() { return this == ALLOW_DOMAIN || this == BLOCK_DOMAIN; }
    public boolean isLabel() { return this == ALLOW_LABEL || this == BLOCK_LABEL; }
    public boolean isBlock() { return this == BLOCK_ADDRESS || this == BLOCK_DOMAIN || this == BLOCK_LABEL; }
}
