# Gmail OAuth setup

The agent reads your Gmail through Google's official API using **OAuth 2.0 for desktop apps** (authorization
code + PKCE, loopback redirect). You sign in on Google's own page. The app never sees or stores your Gmail
password. Refresh tokens are encrypted at rest (AES-256-GCM, key in `<data-dir>\keys\secrets.key`).

> Google's documentation could not be re-checked from the build environment when this was written, so the endpoints
> and scopes below are Google's standard published values. All of them are configurable (`app.gmail.*`) if Google
> changes anything.

## 1. Create the Google Cloud OAuth client (one time)
1. Open https://console.cloud.google.com/, then create a project (e.g. "C2C Job Agent").
2. Go to **APIs & Services → Library**, search for **Gmail API** and click **Enable**.
3. Go to **APIs & Services → OAuth consent screen** (Google Auth Platform → Branding / Audience):
   - User type **External**. App name e.g. "C2C Job Agent (personal)". Add your email as support/developer contact.
   - Under **Audience → Test users**, add the Gmail address(es) you will connect. While the app is in
     *Testing* status only these users can sign in, which is fine for personal use.
   - Note: in Testing status Google may expire refresh tokens after about 7 days. When that happens the account
     shows **Reauth required** and you click **Reconnect**.
4. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type **Desktop app**, name e.g. "C2C Agent desktop".
   - Click **Download JSON**. Save it outside the repository, e.g. `%USERPROFILE%\C2CAgent\google-oauth-client.json`.
5. In the repository's `.env` add:
   ```
   C2C_GMAIL_CLIENT_FILE=C:\Users\<you>\C2CAgent\google-oauth-client.json
   ```
   (Alternatively set `C2C_GMAIL_CLIENT_ID` and `C2C_GMAIL_CLIENT_SECRET`. For desktop clients Google treats the
   "secret" as non-confidential, but it is still kept out of git.)
6. Restart the app (`Stop-C2CAgent.cmd`, then `Start-C2CAgent.cmd`).

## 2. Connect your mailbox
1. Go to **Gmail Accounts → Connect Gmail account**.
2. Google's consent screen opens. Choose the account and allow the requested permissions:
   - `gmail.readonly`: read messages and attachments.
   - `gmail.compose`: create drafts and send. It is used only after you approve an application (Phase 8), and
     outbound sending stays in dry-run until you enable it.
   - No delete/modify scope is requested.
   - Because the app is unverified, Google shows a warning. Choose *Advanced → Go to … (unsafe)*: this is your own app.
3. You are redirected back to `http://127.0.0.1:8080/api/v1/gmail/oauth/callback`, and then to the Gmail Accounts page.
   The first sync is queued immediately: it backfills the last **N days** (Gmail Accounts → Synchronization,
   default 14).

You can connect several Gmail accounts. Each has its own checkpoint, status, pause switch and source rules.

## How sync works
- **Initial backfill:** the current Gmail `historyId` is recorded first, then messages from the last N days are
  listed (`after:<epoch>`) and fetched in raw RFC 822 form.
- **Incremental:** `history.list` from the stored `historyId`, every *polling interval* minutes (default 15).
- **Checkpoint expired** (Gmail returns 404 for very old history ids): a bounded re-scan since the last successful
  sync, recorded in the audit log.
- **Idempotent:** each message is stored once per mailbox (unique Gmail message id), so restarts, retries and
  duplicate history events never create duplicates.
- **Errors:** network, 5xx and rate limits are retried with exponential backoff (Settings → Background jobs).
  A revoked or expired refresh token (`invalid_grant`) or missing permission sets the account to
  **Reauth required**; click **Reconnect**.
- **Own sent messages** (`SENT` label or sender = the mailbox itself) are stored as `OWN_SENT` and never processed
  as requirements.
- **Disconnect** revokes the token at Google (best effort) and deletes the stored tokens. Synced emails remain.

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| "Google OAuth client not configured" | `C2C_GMAIL_CLIENT_FILE` is not set or the file is unreadable; restart after fixing `.env` |
| Google says `redirect_uri_mismatch` | The client must be type **Desktop app** (loopback redirects are allowed only for that type) |
| Google says "Access blocked: app has not completed verification" | Add your Gmail address under OAuth consent screen → Test users |
| `access_denied` banner | You cancelled consent; click Connect again |
| `no_refresh_token` banner | Remove the app at https://myaccount.google.com/permissions and connect again |
| Account shows **Reauth required** every week | Normal for apps in Testing status; click Reconnect (or publish the app for personal use) |
| Port 8080 changed | The redirect URI follows `C2C_PORT` automatically (`http://127.0.0.1:<port>/api/v1/gmail/oauth/callback`) |
