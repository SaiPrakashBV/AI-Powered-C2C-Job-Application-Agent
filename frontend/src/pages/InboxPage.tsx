import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { EmailDetail, EmailSummary, Page, ProcessingStatus, SourceDecision } from '../api/types';
import { Alert, Badge, Card, Pagination, Spinner, formatDateTime } from '../components/ui';

const decisionTone = (d: SourceDecision) => (d === 'ALLOWED' ? 'green' : d === 'DISCOVERY' ? 'amber' : d === 'BLOCKED' ? 'red' : 'slate');
const statusLabel: Record<ProcessingStatus, string> = {
  PENDING_CLASSIFICATION: 'Awaiting classification',
  EXCLUDED: 'Excluded',
  PARSE_ERROR: 'Parse error',
  NEEDS_REVIEW: 'Needs review',
  CLASSIFIED: 'Classified',
};

export function InboxPage() {
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [decision, setDecision] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const list = useQuery({
    queryKey: ['emails', page, q, decision],
    queryFn: () =>
      api<Page<EmailSummary>>(`/api/v1/emails?page=${page}&size=25${q ? '&q=' + encodeURIComponent(q) : ''}${decision ? '&decision=' + decision : ''}`),
    refetchInterval: 15_000,
  });

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Inbox</h1>
      <Alert tone="amber" title="Classification arrives in Phase 3">
        Synced emails are shown with their source-rule decision. Detecting which ones are new job requirements (versus
        replies, interviews or newsletters) is not implemented yet, so nothing here triggers an application.
      </Alert>
      <div className="grid gap-6 xl:grid-cols-2">
        <Card
          title={`Synced emails (${list.data?.totalItems ?? 0})`}
          actions={
            <div className="flex gap-2">
              <input aria-label="Search emails" placeholder="Search sender, subject…" value={q}
                onChange={(e) => { setQ(e.target.value); setPage(0); }}
                className="w-48 rounded-md px-2 py-1 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600" />
              <select aria-label="Filter by source decision" value={decision} onChange={(e) => { setDecision(e.target.value); setPage(0); }}
                className="rounded-md px-2 py-1 text-sm ring-1 ring-slate-300 dark:bg-slate-800 dark:ring-slate-600">
                <option value="">All sources</option>
                <option value="ALLOWED">Allowed</option>
                <option value="DISCOVERY">Discovery</option>
                <option value="BLOCKED">Blocked</option>
                <option value="UNLISTED">Unlisted</option>
                <option value="OWN_SENT">Own sent</option>
              </select>
            </div>
          }
        >
          {list.isLoading ? <Spinner /> : list.error ? <Alert>{(list.error as Error).message}</Alert> : list.data?.items.length === 0 ? (
            <p className="text-sm text-slate-500">No emails yet. Connect a Gmail account and run a sync.</p>
          ) : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-800">
              {list.data?.items.map((e) => (
                <li key={e.id}>
                  <button onClick={() => setSelected(e.id)}
                    className={'w-full px-2 py-2 text-left text-sm hover:bg-slate-50 dark:hover:bg-slate-800 ' + (selected === e.id ? 'bg-indigo-50 dark:bg-indigo-950' : '')}>
                    <div className="flex justify-between gap-2">
                      <span className="truncate font-medium">{e.fromName || e.fromAddress || '(unknown sender)'}</span>
                      <span className="shrink-0 text-xs text-slate-500">{formatDateTime(e.internalDate)}</span>
                    </div>
                    <div className="truncate">{e.subject ?? <em className="text-slate-500">(no subject)</em>}</div>
                    <div className="mt-1 flex flex-wrap gap-1">
                      <Badge tone={decisionTone(e.sourceDecision)}>{e.sourceDecision}</Badge>
                      <Badge>{statusLabel[e.processingStatus]}</Badge>
                      {e.hasWarnings && <Badge tone="amber">Warnings</Badge>}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {list.data && <Pagination page={page} totalPages={list.data.totalPages} onPage={setPage} />}
        </Card>
        {selected ? <EmailDetailCard id={selected} /> : <Card><p className="text-sm text-slate-500">Select an email to see its content.</p></Card>}
      </div>
    </div>
  );
}

function EmailDetailCard({ id }: { id: string }) {
  const d = useQuery({ queryKey: ['email', id], queryFn: () => api<EmailDetail>(`/api/v1/emails/${id}`) });
  if (d.isLoading) return <Card><Spinner /></Card>;
  if (d.error || !d.data) return <Card><Alert>{(d.error as Error)?.message ?? 'Not found'}</Alert></Card>;
  const e = d.data;
  const body = e.textBody ?? e.htmlText;
  return (
    <Card title={e.subject ?? '(no subject)'}>
      <dl className="mb-3 grid grid-cols-[6rem_1fr] gap-x-3 gap-y-1 text-sm">
        <dt className="text-slate-500">From</dt><dd className="break-all">{e.fromName ? `${e.fromName} <${e.fromAddress}>` : e.fromAddress ?? '—'}</dd>
        {e.replyTo && (<><dt className="text-slate-500">Reply-To</dt><dd className="break-all">{e.replyTo}</dd></>)}
        <dt className="text-slate-500">To</dt><dd className="break-all">{e.to ?? '—'}</dd>
        <dt className="text-slate-500">Date</dt><dd>{formatDateTime(e.internalDate)}</dd>
        <dt className="text-slate-500">Source</dt><dd><Badge tone={decisionTone(e.sourceDecision)}>{e.sourceDecision}</Badge> <span className="text-xs">{e.sourceExplanation}</span></dd>
      </dl>
      {e.parseWarnings && <div className="mb-3"><Alert tone="amber" title="Parsing warnings"><pre className="whitespace-pre-wrap text-xs">{e.parseWarnings}</pre></Alert></div>}
      {e.attachments.length > 0 && (
        <div className="mb-3">
          <h3 className="mb-1 text-sm font-semibold">Attachments</h3>
          <ul className="space-y-1 text-sm">
            {e.attachments.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center gap-2">
                {a.downloadable ? (
                  <a className="text-indigo-600 hover:underline" href={`/api/v1/emails/attachments/${a.id}/download`}>{a.filename}</a>
                ) : <span>{a.filename}</span>}
                <span className="text-xs text-slate-500">{Math.round(a.sizeBytes / 1024)} KB</span>
                <Badge tone={a.extractionStatus === 'EXTRACTED' ? 'green' : a.extractionStatus === 'UNSUPPORTED' ? 'slate' : 'amber'}>{a.extractionStatus}</Badge>
                {a.extractionError && <span className="text-xs text-amber-700 dark:text-amber-300">{a.extractionError}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {body ? (
        <pre className="max-h-[32rem] overflow-auto whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-xs dark:bg-slate-950">{body}</pre>
      ) : (
        <p className="text-sm text-slate-500">
          {e.processingStatus === 'EXCLUDED' ? 'Body not stored: this message was excluded by source rules (privacy).' : 'No text content.'}
        </p>
      )}
    </Card>
  );
}
