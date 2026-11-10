import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import type { EvaluateResult, GmailAccount, ImportResult, Settings, SourceRule, SourceRuleType } from '../api/types';
import { Alert, Badge, Button, Card, Spinner, TextField } from '../components/ui';

export const RULE_LABELS: Record<SourceRuleType, string> = {
  ALLOW_ADDRESS: 'Approved address',
  ALLOW_DOMAIN: 'Approved domain',
  TRUSTED_FORWARDER: 'Trusted forwarder',
  ALLOW_LABEL: 'Approved Gmail label',
  BLOCK_ADDRESS: 'Blocked address',
  BLOCK_DOMAIN: 'Blocked domain',
  BLOCK_LABEL: 'Blocked Gmail label',
};
const isBlock = (t: SourceRuleType) => t.startsWith('BLOCK');

export function SourceRulesPage() {
  const qc = useQueryClient();
  const rules = useQuery({ queryKey: ['source-rules'], queryFn: () => api<SourceRule[]>('/api/v1/source-rules') });
  const accounts = useQuery({ queryKey: ['gmail-accounts'], queryFn: () => api<GmailAccount[]>('/api/v1/gmail/accounts') });
  const precedence = useQuery({ queryKey: ['source-rules-precedence'], queryFn: () => api<{ precedence: string }>('/api/v1/source-rules/precedence') });
  const [filter, setFilter] = useState('');
  const refresh = () => qc.invalidateQueries({ queryKey: ['source-rules'] });

  const update = useMutation({
    mutationFn: (r: SourceRule) =>
      api<SourceRule>(`/api/v1/source-rules/${r.id}`, {
        method: 'PUT',
        json: { ruleType: r.ruleType, matchValue: r.matchValue, gmailAccountId: r.gmailAccountId, priority: r.priority, enabled: r.enabled, note: r.note },
      }),
    onSuccess: refresh,
  });
  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/source-rules/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  });

  const visible = (rules.data ?? []).filter((r) => !filter || r.matchValue.includes(filter.toLowerCase()) || RULE_LABELS[r.ruleType].toLowerCase().includes(filter.toLowerCase()));
  const accountName = (id: string | null) => (id ? accounts.data?.find((a) => a.id === id)?.emailAddress ?? 'unknown account' : 'All accounts');

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Email Source &amp; Vendor Rules</h1>
      <Alert tone="indigo" title="An approved sender is not automatically a job">
        Rules only decide which emails are <em>eligible</em>. Every eligible email still goes through content classification,
        so vendor replies, interview notices and newsletters from approved senders never create applications.
      </Alert>
      <div className="grid gap-6 lg:grid-cols-3">
        <AddRuleCard accounts={accounts.data ?? []} onAdded={refresh} />
        <TestSenderCard />
        <DiscoveryCard />
      </div>
      <Card
        title={`Rules (${rules.data?.length ?? 0})`}
        actions={
          <div className="flex items-center gap-2">
            <input aria-label="Filter rules" placeholder="Filter…" value={filter} onChange={(e) => setFilter(e.target.value)}
              className="w-48 rounded-md px-2 py-1 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600" />
            <ImportExport onImported={refresh} />
          </div>
        }
      >
        {(update.error || remove.error) && <div className="mb-3"><Alert>{((update.error ?? remove.error) as Error).message}</Alert></div>}
        {rules.isLoading ? <Spinner /> : visible.length === 0 ? (
          <p className="text-sm text-slate-500">No rules yet. Add approved recruiter addresses or vendor domains above.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-slate-500">
                <tr><th className="py-2">Type</th><th>Value</th><th>Applies to</th><th>Priority</th><th>Enabled</th><th>Note</th><th><span className="sr-only">Actions</span></th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {visible.map((r) => (
                  <tr key={r.id} className={r.enabled ? '' : 'opacity-50'}>
                    <td className="py-2"><Badge tone={isBlock(r.ruleType) ? 'red' : 'green'}>{RULE_LABELS[r.ruleType]}</Badge></td>
                    <td className="font-mono text-xs">{r.matchValue}</td>
                    <td className="text-xs">{accountName(r.gmailAccountId)}</td>
                    <td>{r.priority}</td>
                    <td>
                      <input type="checkbox" aria-label={`Enable rule ${r.matchValue}`} checked={r.enabled}
                        onChange={(e) => update.mutate({ ...r, enabled: e.target.checked })} />
                    </td>
                    <td className="max-w-xs truncate text-xs" title={r.note ?? ''}>{r.note ?? ''}</td>
                    <td className="text-right">
                      <Button variant="ghost" onClick={() => { if (window.confirm(`Delete rule ${r.matchValue}?`)) remove.mutate(r.id); }}>Delete</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {precedence.data && (
        <Card title="How conflicting rules are resolved">
          <pre className="whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">{precedence.data.precedence}</pre>
        </Card>
      )}
    </div>
  );
}

function AddRuleCard({ accounts, onAdded }: { accounts: GmailAccount[]; onAdded: () => void }) {
  const [ruleType, setRuleType] = useState<SourceRuleType>('ALLOW_ADDRESS');
  const [value, setValue] = useState('');
  const [priority, setPriority] = useState('100');
  const [accountId, setAccountId] = useState('');
  const [note, setNote] = useState('');
  const create = useMutation({
    mutationFn: () =>
      api<SourceRule>('/api/v1/source-rules', {
        method: 'POST',
        json: { ruleType, matchValue: value, priority: Number(priority), gmailAccountId: accountId || null, note: note || null },
      }),
    onSuccess: () => { setValue(''); setNote(''); onAdded(); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };
  const placeholder = ruleType.endsWith('DOMAIN') ? 'staffing-company.com' : ruleType.endsWith('LABEL') ? 'Label_123' : 'recruiter@vendor.com';
  return (
    <Card title="Add rule">
      <form onSubmit={submit} className="space-y-3">
        <div className="space-y-1">
          <label htmlFor="rule-type" className="block text-sm font-medium">Rule type</label>
          <select id="rule-type" value={ruleType} onChange={(e) => setRuleType(e.target.value as SourceRuleType)}
            className="block w-full rounded-md bg-white px-3 py-2 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600">
            {Object.entries(RULE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <TextField label="Value" placeholder={placeholder} value={value} onChange={(e) => setValue(e.target.value)} required />
        <div className="grid grid-cols-2 gap-2">
          <TextField label="Priority" type="number" min={0} max={10000} value={priority} onChange={(e) => setPriority(e.target.value)} hint="Lower runs first" />
          <div className="space-y-1">
            <label htmlFor="rule-account" className="block text-sm font-medium">Gmail account</label>
            <select id="rule-account" value={accountId} onChange={(e) => setAccountId(e.target.value)}
              className="block w-full rounded-md bg-white px-3 py-2 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600">
              <option value="">All accounts</option>
              {accounts.map((a) => <option key={a.id} value={a.id}>{a.emailAddress}</option>)}
            </select>
          </div>
        </div>
        <TextField label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        {create.error && <Alert>{(create.error as ApiError).message}</Alert>}
        <Button type="submit" loading={create.isPending} disabled={!value}>Add rule</Button>
      </form>
    </Card>
  );
}

function TestSenderCard() {
  const [from, setFrom] = useState('');
  const evaluate = useMutation({
    mutationFn: () => api<EvaluateResult>('/api/v1/source-rules/evaluate', { method: 'POST', json: { fromAddress: from } }),
  });
  const tone = (d: string) => (d === 'ALLOWED' ? 'green' : d === 'DISCOVERY' ? 'amber' : 'red') as 'green' | 'amber' | 'red';
  return (
    <Card title="Test a sender">
      <form onSubmit={(e) => { e.preventDefault(); evaluate.mutate(); }} className="space-y-3">
        <TextField label="Sender address" placeholder="jobs@vendor.com" value={from} onChange={(e) => setFrom(e.target.value)} />
        <Button type="submit" variant="secondary" disabled={!from} loading={evaluate.isPending}>Evaluate</Button>
      </form>
      {evaluate.data && (
        <div className="mt-3 space-y-1 text-sm">
          <Badge tone={tone(evaluate.data.decision)}>{evaluate.data.decision}</Badge>
          <p>{evaluate.data.explanation}</p>
        </div>
      )}
      {evaluate.error && <div className="mt-3"><Alert>{(evaluate.error as Error).message}</Alert></div>}
    </Card>
  );
}

function DiscoveryCard() {
  const qc = useQueryClient();
  const s = useQuery({ queryKey: ['settings'], queryFn: () => api<Settings>('/api/v1/settings') });
  const save = useMutation({
    mutationFn: (enabled: boolean) =>
      api<Settings>('/api/v1/settings', {
        method: 'PUT',
        json: {
          sessionTimeoutMinutes: s.data!.sessionTimeoutMinutes,
          gmailPollingIntervalMinutes: s.data!.gmailPollingIntervalMinutes,
          externalAiConsent: s.data!.externalAiConsent,
          theme: s.data!.theme,
          sourceDiscoveryMode: enabled,
        },
      }),
    onSuccess: (d) => qc.setQueryData(['settings'], d),
  });
  if (!s.data) return <Card title="Discovery mode"><Spinner /></Card>;
  return (
    <Card title="Discovery mode">
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" className="mt-1" checked={s.data.sourceDiscoveryMode} onChange={(e) => save.mutate(e.target.checked)} />
        <span>
          Also evaluate emails from <strong>unknown senders</strong> (no matching rule) as possible new recruiters.
          Blocked senders are always excluded.
        </span>
      </label>
      {save.error && <div className="mt-3"><Alert>{(save.error as Error).message}</Alert></div>}
    </Card>
  );
}

function ImportExport({ onImported }: { onImported: () => void }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const exportRules = async () => {
    const data = await api<unknown[]>('/api/v1/source-rules/export');
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'c2c-source-rules.json';
    a.click();
    URL.revokeObjectURL(url);
  };
  const importFile = async (f: File) => {
    setError(null);
    setResult(null);
    try {
      const parsed = JSON.parse(await f.text());
      const rules = Array.isArray(parsed) ? parsed : parsed.rules;
      const r = await api<ImportResult>('/api/v1/source-rules/import', { method: 'POST', json: { rules } });
      setResult(r);
      onImported();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Import failed');
    }
  };
  return (
    <div className="flex items-center gap-2">
      <Button variant="secondary" onClick={exportRules}>Export</Button>
      <Button variant="secondary" onClick={() => fileRef.current?.click()}>Import</Button>
      <input ref={fileRef} type="file" accept="application/json,.json" className="hidden" aria-label="Import rules file"
        onChange={(e) => { const f = e.target.files?.[0]; if (f) importFile(f); e.target.value = ''; }} />
      {result && <span className="text-xs text-green-700">Imported {result.created}, skipped {result.skippedDuplicates}{result.errors.length ? `, ${result.errors.length} error(s)` : ''}</span>}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}
