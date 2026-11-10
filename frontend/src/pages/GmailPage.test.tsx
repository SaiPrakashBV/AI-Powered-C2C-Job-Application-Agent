import { screen } from '@testing-library/react';
import { GmailPage } from './GmailPage';
import { mockFetch, renderApp } from '../test/utils';

const settings = { automationMode: 'MANUAL_APPROVAL', emergencyStop: false, outboundDryRun: true, sessionTimeoutMinutes: 60,
  gmailPollingIntervalMinutes: 15, externalAiConsent: false, theme: 'light', sourceDiscoveryMode: false, gmailBackfillDays: 14, version: 0, updatedAt: '' };

describe('GmailPage', () => {
  it('explains missing OAuth configuration and disables connect', async () => {
    mockFetch({
      'GET /api/v1/gmail/oauth/status': { status: 200, body: { oauthConfigured: false, configSource: null, redirectUri: 'http://127.0.0.1:8080/api/v1/gmail/oauth/callback', scopes: [] } },
      'GET /api/v1/gmail/accounts': { status: 200, body: [] },
      'GET /api/v1/settings': { status: 200, body: settings },
    });
    renderApp(<GmailPage />);
    expect(await screen.findByText(/Google OAuth client not configured/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Connect Gmail account' })).toBeDisabled();
  });

  it('shows accounts needing reconnection and OAuth errors from the redirect', async () => {
    mockFetch({
      'GET /api/v1/gmail/oauth/status': { status: 200, body: { oauthConfigured: true, configSource: 'file', redirectUri: 'x', scopes: ['https://www.googleapis.com/auth/gmail.readonly'] } },
      'GET /api/v1/gmail/accounts': { status: 200, body: [{ id: 'a1', emailAddress: 'me@gmail.com', status: 'REAUTH_REQUIRED', syncEnabled: true,
        lastSyncAt: null, lastSyncStatus: 'REAUTH_REQUIRED', lastError: 'Google access was revoked', connectedAt: null, messagesSynced: 12,
        backfillCompletedAt: null, historyId: null, grantedScopes: null }] },
      'GET /api/v1/settings': { status: 200, body: settings },
    });
    renderApp(<GmailPage />, '/gmail?oauthError=access_denied');
    expect(await screen.findByText('me@gmail.com')).toBeInTheDocument();
    expect(screen.getByText('REAUTH REQUIRED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reconnect' })).toBeEnabled();
    expect(screen.getByText(/cancelled the Google consent screen/)).toBeInTheDocument();
  });
});
