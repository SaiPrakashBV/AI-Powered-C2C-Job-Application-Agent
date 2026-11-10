import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/client';
import type { AuditEvent, Diagnostics, Job, Page, Settings, Theme } from '../api/types';
import { Alert, Badge, Button, Card, Modal, Pagination, Spinner, TextField, formatDateTime } from '../components/ui';

export function SettingsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Settings &amp; Diagnostics</h1>
      <SafetyCard />
      <GeneralSettingsCard />
      <DiagnosticsCard />
      <JobsCard />
      <AuditCard />
    </div>
  );
}

function useSettings() {
  return useQuery({ queryKey: ['settings'], queryFn: () => api<Settings>('/api/v1/settings') });
}

function SafetyCard() {
  const qc = useQueryClient();
  const s = useSettings();
  const [confirm, setConfirm] = useState(false);
  const [reason, setReason] = useState('');
  const toggle = useMutation({
    mutationFn: (engaged: boolean) => api<Settings>('/api/v1/settings/emergency-stop', { method: 'POST', json: { engaged, reason } }),
    onSuccess: (data) => {
      qc.setQueryData(['settings'], data);
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      setConfirm(false);
      setReason('');
    },
  });
  if (!s.data) return <Spinner />;
  const engaged = s.data.emergencyStop;
  return (
    <Card title="Sending safety">
      <dl className="mb-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-slate-500">Automation mode</dt>
          <dd className="font-medium">Manual approval (required in this release)</dd>
        </div>
        <div>
          <dt className="text-slate-500">Outbound</dt>
          <dd>{s.data.outboundDryRun ? <Badge tone="amber">Dry-run — no real emails</Badge> : <Badge tone="red">Live</Badge>}</dd>
        </div>
        <div>
          <dt className="text-slate-500">Emergency stop</dt>
          <dd>{engaged ? <Badge tone="red">Engaged</Badge> : <Badge tone="green">Not engaged</Badge>}</dd>
        </div>
      </dl>
      <Button variant={engaged ? 'secondary' : 'danger'} onClick={() => setConfirm(true)}>
        {engaged ? 'Release emergency stop' : 'Engage emergency stop'}
      </Button>
      <Modal
        open={confirm}
        title={engaged ? 'Release emergency stop?' : 'Engage emergency stop?'}
        onClose={() => setConfirm(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(false)}>
              Cancel
            </Button>
            <Button variant={engaged ? 'primary' : 'danger'} loading={toggle.isPending} onClick={() => toggle.mutate(!engaged)}>
              Confirm
            </Button>
          </>
        }
      >
        <p className="mb-3">
          {engaged
            ? 'Sending will again follow the approval rules. Manual approval is still required for every application.'
            : 'All outbound sending — manual and automatic — will be blocked until you release it.'}
        </p>
        <TextField label="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} />
        {toggle.error && <div className="mt-3"><Alert>{(toggle.error as Error).message}</Alert></div>}
      </Modal>
    </Card>
  );
}

function GeneralSettingsCard() {
  const qc = useQueryClient();
  const s = useSettings();
  const [form, setForm] = useState<{ sessionTimeoutMinutes: string; gmailPollingIntervalMinutes: string; externalAiConsent: boolean; theme: Theme } | null>(null);
  useEffect(() => {
    if (s.data && !form)
      setForm({
        sessionTimeoutMinutes: String(s.data.sessionTimeoutMinutes),
        gmailPollingIntervalMinutes: String(s.data.gmailPollingIntervalMinutes),
        externalAiConsent: s.data.externalAiConsent,
        theme: s.data.theme,
      });
  }, [s.data, form]);
  const save = useMutation({
    mutationFn: () =>
      api<Settings>('/api/v1/settings', {
        method: 'PUT',
        json: {
          sessionTimeoutMinutes: Number(form!.sessionTimeoutMinutes),
          gmailPollingIntervalMinutes: Number(form!.gmailPollingIntervalMinutes),
          externalAiConsent: form!.externalAiConsent,
          theme: form!.theme,
        },
      }),
    onSuccess: (data) => {
      qc.setQueryData(['settings'], data);
      qc.invalidateQueries({ queryKey: ['audit'] });
    },
  });
  if (!form) return <Spinner />;
  const err = save.error instanceof ApiError ? save.error : null;
  const fe = (f: string) => err?.fieldErrors.find((x) => x.field === f)?.message;
  return (
    <Card title="General">
      <form
        className="grid gap-4 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <TextField label="Session timeout (minutes)" type="number" min={5} max={1440} value={form.sessionTimeoutMinutes}
          onChange={(e) => setForm({ ...form, sessionTimeoutMinutes: e.target.value })} error={fe('sessionTimeoutMinutes')}
          hint="Applies from your next login." />
        <TextField label="Gmail polling interval (minutes)" type="number" min={1} max={1440} value={form.gmailPollingIntervalMinutes}
          onChange={(e) => setForm({ ...form, gmailPollingIntervalMinutes: e.target.value })} error={fe('gmailPollingIntervalMinutes')}
          hint="Used once Gmail sync is available (Phase 2)." />
        <div className="space-y-1">
          <label htmlFor="theme" className="block text-sm font-medium">Theme</label>
          <select id="theme" value={form.theme} onChange={(e) => setForm({ ...form, theme: e.target.value as Theme })}
            className="block w-full rounded-md bg-white px-3 py-2 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600">
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </div>
        <label className="flex items-start gap-2 text-sm sm:col-span-2">
          <input type="checkbox" className="mt-1" checked={form.externalAiConsent}
            onChange={(e) => setForm({ ...form, externalAiConsent: e.target.checked })} />
          <span>
            I consent to sending resume and job description content to external AI providers (Claude / OpenAI APIs)
            that I configure. Local AI (Ollama) and manual browser handoff do not require this.
          </span>
        </label>
        <div className="flex items-center gap-3 sm:col-span-2">
          <Button type="submit" loading={save.isPending}>Save settings</Button>
          {save.isSuccess && <span className="text-sm text-green-600">Saved.</span>}
          {save.error && !fe('sessionTimeoutMinutes') && !fe('gmailPollingIntervalMinutes') && (
            <span className="text-sm text-red-600">{(save.error as Error).message}</span>
          )}
        </div>
      </form>
    </Card>
  );
}

function DiagnosticsCard() {
  const qc = useQueryClient();
  const d = useQuery({ queryKey: ['diagnostics'], queryFn: () => api<Diagnostics>('/api/v1/diagnostics') });
  const selfTest = useMutation({
    mutationFn: () => api<Job>('/api/v1/diagnostics/self-test', { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  });
  return (
    <Card title="Diagnostics" actions={<Button variant="secondary" loading={selfTest.isPending} onClick={() => selfTest.mutate()}>Run self-test</Button>}>
      {d.isLoading && <Spinner />}
      {d.error && <Alert>{(d.error as Error).message}</Alert>}
      {d.data && (
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          <Row k="Java" v={d.data.javaVersion} />
          <Row k="Operating system" v={d.data.os} />
          <Row k="PostgreSQL" v={d.data.databaseVersion} />
          <Row k="Schema version" v={d.data.schemaVersion} />
          <Row k="Data directory" v={d.data.dataDirectory} />
          <Row k="Data directory writable" v={d.data.dataDirectoryWritable ? 'Yes' : 'No'} />
          <Row k="Background worker" v={d.data.jobWorkerEnabled ? 'Running' : 'Disabled'} />
          <Row k="Max JVM memory" v={d.data.maxMemoryMb + ' MB'} />
        </dl>
      )}
      {selfTest.isSuccess && <p className="mt-3 text-sm text-slate-500">Self-test queued; its result appears under Background jobs.</p>}
      <p className="mt-3 text-xs text-slate-500">Backup and restore scripts arrive in Phase 10 (see PROGRESS.md).</p>
    </Card>
  );
}

const Row = ({ k, v }: { k: string; v: string }) => (
  <div className="flex justify-between gap-3 border-b border-slate-100 py-1 dark:border-slate-800">
    <dt className="text-slate-500">{k}</dt>
    <dd className="break-all text-right font-mono text-xs">{v}</dd>
  </div>
);

const statusTone = (s: string) => (s === 'SUCCEEDED' ? 'green' : s === 'FAILED' || s === 'DEAD' ? 'red' : s === 'RUNNING' ? 'indigo' : 'slate');

function JobsCard() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const jobs = useQuery({
    queryKey: ['jobs', page],
    queryFn: () => api<Page<Job>>(`/api/v1/jobs?page=${page}&size=10`),
    refetchInterval: 5000,
  });
  const action = useMutation({
    mutationFn: ({ id, op }: { id: string; op: 'retry' | 'cancel' }) => api<Job>(`/api/v1/jobs/${id}/${op}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['jobs'] }),
  });
  return (
    <Card title={<span id="jobs">Background jobs</span>}>
      {action.error && <div className="mb-3"><Alert>{(action.error as Error).message}</Alert></div>}
      {jobs.isLoading ? <Spinner /> : jobs.data && jobs.data.items.length === 0 ? (
        <p className="text-sm text-slate-500">No background jobs yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr><th className="py-2">Type</th><th>Status</th><th>Attempts</th><th>Created</th><th>Last error</th><th><span className="sr-only">Actions</span></th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {jobs.data?.items.map((j) => (
                <tr key={j.id}>
                  <td className="py-2 font-mono text-xs">{j.jobType}</td>
                  <td><Badge tone={statusTone(j.status)}>{j.status}</Badge></td>
                  <td>{j.attempts}/{j.maxAttempts}</td>
                  <td className="text-xs">{formatDateTime(j.createdAt)}</td>
                  <td className="max-w-xs truncate text-xs" title={j.lastError ?? ''}>{j.lastError ?? '—'}</td>
                  <td className="text-right">
                    {['FAILED', 'DEAD', 'CANCELLED'].includes(j.status) && (
                      <Button variant="ghost" onClick={() => action.mutate({ id: j.id, op: 'retry' })}>Retry</Button>
                    )}
                    {j.status === 'QUEUED' && (
                      <Button variant="ghost" onClick={() => action.mutate({ id: j.id, op: 'cancel' })}>Cancel</Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {jobs.data && <Pagination page={page} totalPages={jobs.data.totalPages} onPage={setPage} />}
    </Card>
  );
}

function AuditCard() {
  const [page, setPage] = useState(0);
  const [type, setType] = useState('');
  const audit = useQuery({
    queryKey: ['audit', page, type],
    queryFn: () => api<Page<AuditEvent>>(`/api/v1/audit-events?page=${page}&size=15${type ? '&eventType=' + encodeURIComponent(type) : ''}`),
  });
  return (
    <Card
      title="Audit log"
      actions={
        <input aria-label="Filter by event type" placeholder="Filter event type (e.g. LOGIN_FAILED)" value={type}
          onChange={(e) => { setType(e.target.value.toUpperCase()); setPage(0); }}
          className="w-64 rounded-md px-2 py-1 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600" />
      }
    >
      {audit.isLoading ? <Spinner /> : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr><th className="py-2">When</th><th>Actor</th><th>Event</th><th>Summary</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {audit.data?.items.map((e) => (
                <tr key={e.id}>
                  <td className="py-2 text-xs">{formatDateTime(e.createdAt)}</td>
                  <td className="text-xs">{e.actorType}</td>
                  <td className="font-mono text-xs">{e.eventType}</td>
                  <td>{e.summary}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {audit.data?.items.length === 0 && <p className="py-2 text-sm text-slate-500">No matching events.</p>}
        </div>
      )}
      {audit.data && <Pagination page={page} totalPages={audit.data.totalPages} onPage={setPage} />}
    </Card>
  );
}
