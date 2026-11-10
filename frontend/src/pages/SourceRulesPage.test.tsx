import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SourceRulesPage } from './SourceRulesPage';
import { mockFetch, renderApp } from '../test/utils';

const settings = { automationMode: 'MANUAL_APPROVAL', emergencyStop: false, outboundDryRun: true, sessionTimeoutMinutes: 60,
  gmailPollingIntervalMinutes: 15, externalAiConsent: false, theme: 'light', sourceDiscoveryMode: false, gmailBackfillDays: 14, version: 0, updatedAt: '' };
const rule = { id: 'r1', ruleType: 'BLOCK_DOMAIN', matchValue: 'promo.example', gmailAccountId: null, priority: 100, enabled: true, note: null, createdAt: '', updatedAt: '' };

describe('SourceRulesPage', () => {
  beforeEach(() => { document.cookie = 'XSRF-TOKEN=t'; });

  it('lists rules, adds a rule and evaluates a sender', async () => {
    let rules = [rule];
    const calls = mockFetch({
      'GET /api/v1/source-rules/precedence': { status: 200, body: { precedence: '1. Exact address rules' } },
      'GET /api/v1/source-rules': () => ({ status: 200, body: rules }),
      'GET /api/v1/gmail/accounts': { status: 200, body: [] },
      'GET /api/v1/settings': { status: 200, body: settings },
      'GET /api/v1/auth/csrf': { status: 200, body: {} },
      'POST /api/v1/source-rules/evaluate': { status: 200, body: { decision: 'ALLOWED', ruleId: 'r2', explanation: 'Sender address jobs@vendor-b.com is approved', continuesToClassification: true } },
      'POST /api/v1/source-rules': (_u, init) => {
        const b = JSON.parse(String(init.body));
        const created = { ...rule, id: 'r2', ruleType: b.ruleType, matchValue: b.matchValue.toLowerCase() };
        rules = [...rules, created];
        return { status: 201, body: created };
      },
    });
    renderApp(<SourceRulesPage />);
    expect(await screen.findByText('promo.example')).toBeInTheDocument();
    expect(screen.getByText(/approved sender is not automatically a job/i)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Value'), 'Jobs@Vendor-B.com');
    await userEvent.click(screen.getByRole('button', { name: 'Add rule' }));
    expect(await screen.findByText('jobs@vendor-b.com')).toBeInTheDocument();
    const post = calls.find((c) => c.method === 'POST' && c.url === '/api/v1/source-rules')!;
    expect(JSON.parse(String(post.init.body))).toMatchObject({ ruleType: 'ALLOW_ADDRESS', priority: 100, gmailAccountId: null });

    await userEvent.type(screen.getByLabelText('Sender address'), 'jobs@vendor-b.com');
    await userEvent.click(screen.getByRole('button', { name: 'Evaluate' }));
    await waitFor(() => expect(screen.getByText(/is approved/)).toBeInTheDocument());
  });

  it('shows server validation errors', async () => {
    mockFetch({
      'GET /api/v1/source-rules/precedence': { status: 200, body: { precedence: '' } },
      'GET /api/v1/source-rules': { status: 200, body: [] },
      'GET /api/v1/gmail/accounts': { status: 200, body: [] },
      'GET /api/v1/settings': { status: 200, body: settings },
      'GET /api/v1/auth/csrf': { status: 200, body: {} },
      'POST /api/v1/source-rules': { status: 400, body: { code: 'INVALID_RULE_VALUE', message: "'nope' is not an email address (name@vendor.com)" } },
    });
    renderApp(<SourceRulesPage />);
    await userEvent.type(await screen.findByLabelText('Value'), 'nope');
    await userEvent.click(screen.getByRole('button', { name: 'Add rule' }));
    expect(await screen.findByText(/is not an email address/)).toBeInTheDocument();
  });
});
