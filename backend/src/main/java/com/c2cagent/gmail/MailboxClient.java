package com.c2cagent.gmail;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * Provider-neutral read access to one mailbox. Implemented by {@link GmailRestMailboxClient}; tests and demo mode
 * use in-memory/fixture implementations. All methods throw {@link MailboxException}.
 */
public interface MailboxClient {
    record Profile(String emailAddress, BigInteger historyId) {}

    record IdPage(List<String> messageIds, String nextPageToken) {}

    record HistoryPage(List<String> addedMessageIds, String nextPageToken, BigInteger historyId) {}

    record RawMessage(String id, String threadId, List<String> labelIds, BigInteger historyId, Instant internalDate,
                      String snippet, byte[] raw) {}

    Profile profile();

    /** Lists message ids matching a Gmail search query (e.g. {@code after:1700000000}), newest first. */
    IdPage listMessages(String query, String pageToken);

    /** Lists messages added since {@code startHistoryId}. Throws HISTORY_EXPIRED when the id is too old. */
    HistoryPage listHistory(BigInteger startHistoryId, String pageToken);

    RawMessage getRawMessage(String messageId);
}
