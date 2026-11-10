import { Navigate, Route, Routes } from 'react-router-dom';
import { useMe, useSetupStatus } from './auth/useAuth';
import { Layout } from './components/Layout';
import { Alert, Spinner } from './components/ui';
import { DashboardPage } from './pages/DashboardPage';
import { GmailPage } from './pages/GmailPage';
import { InboxPage } from './pages/InboxPage';
import { SourceRulesPage } from './pages/SourceRulesPage';
import { LoginPage } from './pages/LoginPage';
import { PlannedPage } from './pages/PlannedPage';
import { SettingsPage } from './pages/SettingsPage';
import { SetupPage } from './pages/SetupPage';

export function App() {
  const setup = useSetupStatus();
  const me = useMe();

  if (setup.isLoading || me.isLoading) {
    return <div className="p-8"><Spinner label="Starting" /></div>;
  }
  if (setup.error || me.error) {
    return (
      <div className="p-8">
        <Alert title="Cannot reach the application server">
          {((setup.error ?? me.error) as Error).message} — make sure the backend is running (scripts\start.ps1), then reload.
        </Alert>
      </div>
    );
  }
  if (setup.data?.setupRequired) {
    return (
      <Routes>
        <Route path="*" element={<SetupPage />} />
      </Routes>
    );
  }
  if (!me.data) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="gmail" element={<GmailPage />} />
        <Route path="source-rules" element={<SourceRulesPage />} />
        <Route path="inbox" element={<InboxPage />} />
        <Route path="login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<PlannedPage />} />
      </Route>
    </Routes>
  );
}
