import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { GmailAccount, GmailOAuthStatus, Job, Settings } from '../api/types';
import { Alert, Badge, Button, Card, Modal, Spinner, TextField, formatDateTime } from '../components/ui';

const OAUTH_ERRORS: Record<string, string> = {
  access_denied: 'You cancelled the Google consent screen. No account was connected.',
  invalid_state: 'The sign-in link expired or was already used. Please click "Connect Gmail account" again.',
  no_refresh_token: 'Google did not return offline access. Remove the app at myaccount.google.com/permissions and reconnect.',
  insufficient_scope: 'Read access to Gmail was not granted. Reconnect and allow all requested permissions.',
  permanent: 'Google rejected the request. Check the OAuth client configuration (GMAIL_OAUTH_SETUP.md).',
  transient: 'Google could not be reached. Check your internet connection and try again.',
};

const statusTone = (s: string) => (s === 'CONNECTED' ? 'green' : s === 'REAUTH_REQUIRED' || s === 'ERROR' ? 'red' : 'slate');

export function GmailPage() {
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [confirmDisconnect, setConfirmDisconnect] = useState<GmailAccount | null>(null);
  const status = useQuery({ queryKey: ['gmail-status'], queryFn: () => api<GmailOAuthStatus>('/api/v1/gmail/oauth/status') });
  const accounts = useQuery({
    queryKey: ['gmail-accounts'],
    queryFn: () => api<GmailAccount[]>('/api/v1/gmail/accounts'),
    refetchInterval: 10_000,
  });
  const connect = useMutation({
    mutationFn: () =>
      api<{ authorizationUrl: string }>(`/api/v1/gmail/oauth/start?returnTo=${encodeURIComponent(window.location.origin)}`, { method: 'POST' }),
    onSuccess: (r) => window.location.assign(r.authorizationUrl),
  });
  const sync = useMutation({
    mutationFn: (id: string) => api<Job>(`/api/v1/gmail/accounts/${id}/sync`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gmail-accounts'] }),
  });
  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      api<GmailAccount>(`/api/v1/gmail/accounts/${id}/sync-enabled`, { method: 'POST', json: { enabled } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gmail-accounts'] }),
  });
  const disconnect = useMutation({
    mutationFn: (id: string) => api<GmailAccount>(`/api/v1/gmail/accounts/${id}/disconnect`, { method: 'POST' }),
    onSuccess: () => {
      setConfirmDisconnect(null);
      qc.invalidateQueries({ queryKey: ['gmail-accounts'] });
    },
  });

  const connected = params.get('connected');
  const oauthError = params.get('oauthError');
  const anyError = connect.error ?? sync.error ?? toggle.error ?? disconnect.error;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Gmail Accounts</h1>
      {connected && (
        <Alert tone="green" title="Gmail connected">
          {connected} is connected. The first synchronization has been queued.{' '}
          <button className="underline" onClick={() => setParams({})}>Dismiss</button>
        </Alert>
      )}
      {oauthError && (
        <Alert title="Gmail was not connected">
          {OAUTH_ERRORS[oauthError] ?? `Google returned: ${oauthError}`}{' '}
          <button className="underline" onClick={() => setParams({})}>Dismiss</button>
        </Alert>
      )}
      {anyError && <Alert>{(anyError as Error).message}</Alert>}

      <Card
        title="Connected mailboxes"
        actions={
          <Button onClick={() => connect.mutate()} loading={connect.isPending} disabled={!status.data?.oauthConfigured}>
            Connect Gmail account
          </Button>
        }
      >
        {status.data && !status.data.oauthConfigured && (
          <div className="mb-4">
            <Alert tone="amber" title="Google OAuth client not configured">
              Create a Google Cloud OAuth client of type <strong>Desktop app</strong>, then set{' '}
              <code>C2C_GMAIL_CLIENT_FILE</code> (path to the downloaded JSON) in <code>.env</code> and restart. Step-by-step
              instructions: GMAIL_OAUTH_SETUP.md. Your Gmail password is never requested or stored.
            </Alert>
          </div>
        )}
        {accounts.isLoading ? (
          <Spinner />
        ) : accounts.data?.length === 0 ? (
          <p className="text-sm text-slate-500">No Gmail account connected yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-slate-500">
                <tr><th className="py-2">Account</th><th>Status</th><th>Last sync</th><th>Messages</th><th>Sync</th><th><span className="sr-only">Actions</span></th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {accounts.data?.map((a) => (
                  <tr key={a.id} className="align-top">
                    <td className="py-2 font-medium">{a.emailAddress}</td>
                    <td>
                      <Badge tone={statusTone(a.status)}>{a.status.replace('_', ' ')}</Badge>
                      {a.lastError && <p className="mt-1 max-w-xs text-xs text-red-600 dark:text-red-400">{a.lastError}</p>}
                    </td>
                    <td className="text-xs">
                      {formatDateTime(a.lastSyncAt)}
                      {a.lastSyncStatus && <div className="text-slate-500">{a.lastSyncStatus}</div>}
                    </td>
                    <td>{a.messagesSynced}</td>
                    <td>
                      {a.status === 'CONNECTED' ? (
                        <label className="flex items-center gap-1 text-xs">
                          <input type="checkbox" checked={a.syncEnabled} onChange={(e) => toggle.mutate({ id: a.id, enabled: e.target.checked })} />
                          {a.syncEnabled ? 'On' : 'Paused'}
                        </label>
                      ) : '—'}
                    </td>
                    <td className="space-x-1 whitespace-nowrap text-right">
                      {a.status === 'CONNECTED' ? (
                        <>
                          <Button variant="secondary" onClick={() => sync.mutate(a.id)} loading={sync.isPending && sync.variables === a.id}>Sync now</Button>
                          <Button variant="ghost" onClick={() => setConfirmDisconnect(a)}>Disconnect</Button>
                        </>
                      ) : (
                        <Button variant="secondary" onClick={() => connect.mutate()} disabled={!status.data?.oauthConfigured}>Reconnect</Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {status.data?.oauthConfigured && (
          <p className="mt-4 text-xs text-slate-500">
            Permissions requested: {status.data.scopes.map((s) => s.replace('https://www.googleapis.com/auth/', '')).join(', ')} ·
            Redirect URI: <code>{status.data.redirectUri}</code>
          </p>
        )}
      </Card>
      <SyncSettingsCard />
      <Modal
        open={!!confirmDisconnect}
        title="Disconnect Gmail account?"
        onClose={() => setConfirmDisconnect(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmDisconnect(null)}>Cancel</Button>
            <Button variant="danger" loading={disconnect.isPending} onClick={() => confirmDisconnect && disconnect.mutate(confirmDisconnect.id)}>Disconnect</Button>
          </>
        }
      >
        <p>
          Access for <strong>{confirmDisconnect?.emailAddress}</strong> will be revoked at Google and the stored tokens deleted.
          Already synced emails stay in the application.
        </p>
      </Modal>
    </div>
  );
}

function SyncSettingsCard() {
  const qc = useQueryClient();
  const s = useQuery({ queryKey: ['settings'], queryFn: () => api<Settings>('/api/v1/settings') });
  const [days, setDays] = useState<string | null>(null);
  const [interval, setInterval] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (d: Settings) =>
      api<Settings>('/api/v1/settings', {
        method: 'PUT',
        json: {
          sessionTimeoutMinutes: d.sessionTimeoutMinutes,
          gmailPollingIntervalMinutes: Number(interval ?? d.gmailPollingIntervalMinutes),
          externalAiConsent: d.externalAiConsent,
          theme: d.theme,
          gmailBackfillDays: Number(days ?? d.gmailBackfillDays),
        },
      }),
    onSuccess: (data) => qc.setQueryData(['settings'], data),
  });
  if (!s.data) return null;
  return (
    <Card title="Synchronization">
      <form className="grid gap-4 sm:grid-cols-3" onSubmit={(e) => { e.preventDefault(); save.mutate(s.data); }}>
        <TextField label="Polling interval (minutes)" type="number" min={1} max={1440}
          value={interval ?? String(s.data.gmailPollingIntervalMinutes)} onChange={(e) => setInterval(e.target.value)} />
        <TextField label="Initial backfill (days)" type="number" min={1} max={365}
          value={days ?? String(s.data.gmailBackfillDays)} onChange={(e) => setDays(e.target.value)}
          hint="How far back the first sync of a newly connected account reads." />
        <div className="flex items-end gap-2">
          <Button type="submit" loading={save.isPending}>Save</Button>
          {save.isSuccess && <span className="text-sm text-green-600">Saved.</span>}
        </div>
        {save.error && <div className="sm:col-span-3"><Alert>{(save.error as Error).message}</Alert></div>}
      </form>
      <p className="mt-3 text-xs text-slate-500">
        Sync runs in the background while the application is running and resumes from its checkpoint after a restart.
      </p>
    </Card>
  );
}
