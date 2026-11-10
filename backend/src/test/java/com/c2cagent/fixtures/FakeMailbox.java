package com.c2cagent.fixtures;

import com.c2cagent.gmail.MailboxClient;
import com.c2cagent.gmail.MailboxException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory mailbox used by sync tests (fixture implementation of {@link MailboxClient}). */
public class FakeMailbox implements MailboxClient {
    public final String email;
    public final Map<String, RawMessage> messages = new LinkedHashMap<>();
    public final List<List<String>> historyPages = new ArrayList<>();
    public BigInteger historyId = BigInteger.valueOf(1000);
    public MailboxException failNext;
    public boolean historyExpired;
    public int rawFetches;

    public FakeMailbox(String email) { this.email = email; }

    public FakeMailbox add(String id, byte[] raw, String... labels) {
        messages.put(id, new RawMessage(id, "t-" + id, List.of(labels), historyId, Instant.parse("2026-11-01T10:00:00Z"),
                "snippet " + id, raw));
        return this;
    }

    private void maybeFail() {
        if (failNext != null) {
            MailboxException e = failNext;
            failNext = null;
            throw e;
        }
    }

    @Override public Profile profile() { maybeFail(); return new Profile(email, historyId); }

    @Override public IdPage listMessages(String query, String pageToken) {
        maybeFail();
        List<String> ids = new ArrayList<>(messages.keySet());
        // two pages to exercise pagination
        int half = (ids.size() + 1) / 2;
        return pageToken == null ? new IdPage(ids.subList(0, half), ids.size() > half ? "p2" : null)
                : new IdPage(ids.subList(half, ids.size()), null);
    }

    @Override public HistoryPage listHistory(BigInteger start, String pageToken) {
        maybeFail();
        if (historyExpired) throw new MailboxException(MailboxException.Kind.HISTORY_EXPIRED, "expired");
        int idx = pageToken == null ? 0 : Integer.parseInt(pageToken);
        if (historyPages.isEmpty()) return new HistoryPage(List.of(), null, historyId);
        String next = idx + 1 < historyPages.size() ? String.valueOf(idx + 1) : null;
        return new HistoryPage(historyPages.get(idx), next, historyId);
    }

    @Override public RawMessage getRawMessage(String id) {
        maybeFail();
        rawFetches++;
        RawMessage m = messages.get(id);
        if (m == null) throw new MailboxException(MailboxException.Kind.NOT_FOUND, "gone");
        return m;
    }
}
