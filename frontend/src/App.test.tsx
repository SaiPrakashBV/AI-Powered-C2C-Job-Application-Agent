import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { mockFetch, renderApp } from './test/utils';

const me = { userId: 'u1', workspaceId: 'w1', username: 'sai', displayName: 'Sai', role: 'OWNER', sessionTimeoutMinutes: 60 };
const settings = {
  automationMode: 'MANUAL_APPROVAL', emergencyStop: false, outboundDryRun: true, sessionTimeoutMinutes: 60,
  gmailPollingIntervalMinutes: 15, externalAiConsent: false, theme: 'light', version: 0, updatedAt: '2026-11-10T10:00:00Z',
};
const summary = {
  displayName: 'Sai',
  safety: { automationMode: 'MANUAL_APPROVAL', emergencyStop: false, outboundDryRun: true, externalAiConsent: false },
  system: { database: 'UP', schemaVersion: '1', demoMode: false },
  jobCounts: { QUEUED: 0, RUNNING: 0, SUCCEEDED: 2, FAILED: 1, DEAD: 0, CANCELLED: 0 },
  recentActivity: [{ id: 'a1', actorType: 'SYSTEM', eventType: 'LOGIN_SUCCEEDED', entityType: null, entityId: null, summary: 'User logged in', details: null, createdAt: '2026-11-10T10:00:00Z' }],
  modules: [
    { key: 'dashboard', name: 'Main Dashboard', phase: 1, availability: 'AVAILABLE' },
    { key: 'gmail', name: 'Gmail Accounts', phase: 2, availability: 'PLANNED' },
  ],
};

describe('App routing and authentication', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test-token';
  });

  it('shows first-time setup when no user exists and validates password confirmation', async () => {
    const calls = mockFetch({
      'GET /api/v1/auth/setup-status': { status: 200, body: { setupRequired: true, demoMode: false } },
      'GET /api/v1/auth/me': { status: 401, body: { code: 'NOT_AUTHENTICATED' } },
    });
    renderApp(<App />);
    expect(await screen.findByText(/first-time setup/i)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Username'), 'sai');
    await userEvent.type(screen.getByLabelText('Display name'), 'Sai');
    await userEvent.type(screen.getByLabelText('Password'), 'Correct-Horse-42');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'different-123');
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Passwords do not match')).toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('/auth/setup') && c.method === 'POST')).toBe(false);
  });

  it('shows login when not authenticated and displays server errors', async () => {
    const calls = mockFetch({
      'GET /api/v1/auth/setup-status': { status: 200, body: { setupRequired: false, demoMode: false } },
      'GET /api/v1/auth/me': { status: 401, body: { code: 'NOT_AUTHENTICATED' } },
      'GET /api/v1/auth/csrf': { status: 200, body: { headerName: 'X-XSRF-TOKEN' } },
      'POST /api/v1/auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' } },
    });
    renderApp(<App />);
    await userEvent.type(await screen.findByLabelText('Username'), 'sai');
    await userEvent.type(screen.getByLabelText('Password'), 'nope-nope-1');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument();
    const login = calls.find((c) => c.url.endsWith('/auth/login'))!;
    expect(new Headers(login.init.headers).get('X-XSRF-TOKEN')).toBe('test-token');
  });

  it('renders the dashboard with real summary data for an authenticated user', async () => {
    mockFetch({
      'GET /api/v1/auth/setup-status': { status: 200, body: { setupRequired: false, demoMode: false } },
      'GET /api/v1/auth/me': { status: 200, body: me },
      'GET /api/v1/settings': { status: 200, body: settings },
      'GET /api/v1/dashboard/summary': { status: 200, body: summary },
    });
    renderApp(<App />);
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Manual approval')).toBeInTheDocument();
    expect(screen.getByText('User logged in')).toBeInTheDocument();
    expect(screen.getByText('Phase 2')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/Dry-run \(no real emails\)/)).toBeInTheDocument());
  });

  it('shows an honest placeholder for screens from later phases', async () => {
    mockFetch({
      'GET /api/v1/auth/setup-status': { status: 200, body: { setupRequired: false, demoMode: false } },
      'GET /api/v1/auth/me': { status: 200, body: me },
      'GET /api/v1/settings': { status: 200, body: settings },
    });
    renderApp(<App />, '/resumes');
    expect(await screen.findByText(/scheduled for/i)).toHaveTextContent('Phase 5');
  });
});
