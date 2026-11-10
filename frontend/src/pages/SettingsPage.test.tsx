import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SettingsPage } from './SettingsPage';
import { mockFetch, renderApp } from '../test/utils';

const settings = {
  automationMode: 'MANUAL_APPROVAL', emergencyStop: false, outboundDryRun: true, sessionTimeoutMinutes: 60,
  gmailPollingIntervalMinutes: 15, externalAiConsent: false, theme: 'light', version: 0, updatedAt: '2026-11-10T10:00:00Z',
};
const emptyPage = { items: [], page: 0, size: 10, totalItems: 0, totalPages: 0 };

describe('SettingsPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=t';
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) { this.setAttribute('open', ''); };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) { this.removeAttribute('open'); };
  });

  it('requires confirmation before engaging the emergency stop', async () => {
    const calls = mockFetch({
      'GET /api/v1/settings': { status: 200, body: settings },
      'GET /api/v1/diagnostics': { status: 200, body: { javaVersion: '21', os: 'Windows 11', databaseVersion: '16', schemaVersion: '1', dataDirectory: 'C:/data', dataDirectoryWritable: true, demoMode: false, jobWorkerEnabled: true, maxMemoryMb: 512 } },
      'GET /api/v1/jobs': { status: 200, body: emptyPage },
      'GET /api/v1/audit-events': { status: 200, body: emptyPage },
      'GET /api/v1/auth/csrf': { status: 200, body: {} },
      'POST /api/v1/settings/emergency-stop': (_u, init) => ({ status: 200, body: { ...settings, emergencyStop: JSON.parse(String(init.body)).engaged } }),
    });
    renderApp(<SettingsPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Engage emergency stop' }));
    expect(calls.some((c) => c.method === 'POST')).toBe(false);
    expect(screen.getByText(/will be blocked until you release it/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Release emergency stop' })).toBeInTheDocument());
    const post = calls.find((c) => c.method === 'POST' && c.url.endsWith('/emergency-stop'))!;
    expect(JSON.parse(String(post.init.body)).engaged).toBe(true);
    expect(screen.getByText('Windows 11')).toBeInTheDocument();
  });
});
