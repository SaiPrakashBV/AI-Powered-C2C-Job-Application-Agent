import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { DashboardSummary } from '../api/types';
import { Alert, Badge, Card, Spinner, Stat, formatDateTime } from '../components/ui';
import { ALL_NAV_ITEMS } from '../navigation';

const MODE_LABEL: Record<string, string> = {
  MANUAL_APPROVAL: 'Manual approval',
  CONDITIONAL: 'Conditional automation',
  RULES_BASED: 'Rules-based automation',
  FULL_AUTONOMOUS: 'Full autonomous',
};

export function DashboardPage() {
  const q = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<DashboardSummary>('/api/v1/dashboard/summary'),
    refetchInterval: 15_000,
  });
  if (q.isLoading) return <Spinner label="Loading dashboard" />;
  if (q.error) return <Alert title="Could not load the dashboard">{(q.error as Error).message}</Alert>;
  const d = q.data!;
  const failedJobs = d.jobCounts.FAILED + d.jobCounts.DEAD;
  const available = d.modules.filter((m) => m.availability === 'AVAILABLE').length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <p className="text-sm text-slate-500">Welcome back, {d.displayName}.</p>
      </div>

      {d.safety.emergencyStop && (
        <Alert title="Emergency stop is engaged">All outbound sending is blocked. Release it in Settings when ready.</Alert>
      )}
      {d.system.demoMode && (
        <Alert tone="amber" title="Demo mode">Sample data may be shown. No real Gmail account is used.</Alert>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Stat label="Sending mode" value={MODE_LABEL[d.safety.automationMode]} />
        <Stat label="Outbound" value={d.safety.outboundDryRun ? 'Dry-run' : 'Live'} />
        <Stat label="Database" value={d.system.database} tone={d.system.database === 'UP' ? 'green' : 'red'} />
        <Stat label="Failed background jobs" value={failedJobs} tone={failedJobs > 0 ? 'red' : undefined} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Background jobs" actions={<Link className="text-sm text-indigo-600 hover:underline" to="/settings#jobs">View all</Link>}>
          <dl className="grid grid-cols-3 gap-3 text-sm">
            {Object.entries(d.jobCounts).map(([status, count]) => (
              <div key={status}>
                <dt className="text-xs text-slate-500">{status}</dt>
                <dd className="text-lg font-semibold">{count}</dd>
              </div>
            ))}
          </dl>
        </Card>

        <Card title="Recent activity">
          {d.recentActivity.length === 0 ? (
            <p className="text-sm text-slate-500">No activity yet.</p>
          ) : (
            <ul className="divide-y divide-slate-100 text-sm dark:divide-slate-800">
              {d.recentActivity.map((e) => (
                <li key={e.id} className="flex justify-between gap-3 py-2">
                  <span>{e.summary}</span>
                  <span className="shrink-0 text-xs text-slate-500">{formatDateTime(e.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card title={`Build progress — ${available} of ${d.modules.length} areas available`}>
        <p className="mb-3 text-sm text-slate-500">
          Pipeline metrics (emails scanned, requirements, matches, applications) will appear here as each module is
          delivered. Nothing below is simulated.
        </p>
        <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {d.modules.map((m) => {
            const nav = ALL_NAV_ITEMS.find((n) => n.moduleKey === m.key);
            return (
              <li key={m.key} className="flex items-center justify-between rounded-md px-3 py-2 ring-1 ring-slate-200 dark:ring-slate-800">
                {nav ? <Link to={nav.path} className="text-sm hover:underline">{m.name}</Link> : <span className="text-sm">{m.name}</span>}
                {m.availability === 'AVAILABLE' ? <Badge tone="green">Available</Badge> : m.availability === 'PARTIAL' ? <Badge tone="amber">Partial · completes in Phase {m.phase}</Badge> : <Badge>Phase {m.phase}</Badge>}
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}
