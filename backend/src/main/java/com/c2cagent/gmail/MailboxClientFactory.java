package com.c2cagent.gmail;

/** Creates a client for a connected account. Replaceable in tests (fixture mailboxes). */
public interface MailboxClientFactory {
    MailboxClient forAccount(GmailAccount account);
}
