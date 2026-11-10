import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Settings } from '../api/types';
import { useLogout, useMe } from '../auth/useAuth';
import { NAV_SECTIONS } from '../navigation';
import { Badge, Button } from './ui';
import { useApplyTheme } from '../theme';

export function Layout() {
  const { data: me } = useMe();
  const logout = useLogout();
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api<Settings>('/api/v1/settings') });
  useApplyTheme(settings.data?.theme);

  return (
    <div className="flex min-h-screen">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:bg-white focus:p-2">
        Skip to content
      </a>
      <aside className="w-60 shrink-0 border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="px-4 py-4">
          <p className="text-lg font-bold text-indigo-600">C2C Job Agent</p>
          <p className="text-xs text-slate-500">Local · single user</p>
        </div>
        <nav aria-label="Main" className="space-y-4 px-2 pb-6">
          {NAV_SECTIONS.map((section) => (
            <div key={section.title}>
              <p className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">{section.title}</p>
              <ul>
                {section.items.map((item) => (
                  <li key={item.path}>
                    <NavLink
                      to={item.path}
                      end={item.path === '/'}
                      className={({ isActive }) =>
                        'flex items-center justify-between rounded-md px-2 py-1.5 text-sm ' +
                        (isActive
                          ? 'bg-indigo-50 font-medium text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300'
                          : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800')
                      }
                    >
                      <span>{item.label}</span>
                      {item.phase > 2 && <span className="text-[10px] text-slate-400">P{item.phase}</span>}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center gap-2 text-sm">
            {settings.data?.emergencyStop ? (
              <Badge tone="red">Emergency stop engaged</Badge>
            ) : (
              <Badge tone="green">Sending: manual approval required</Badge>
            )}
            {settings.data?.outboundDryRun && <Badge tone="amber">Dry-run (no real emails)</Badge>}
          </div>
          <div className="flex items-center gap-3 text-sm">
            <span className="text-slate-600 dark:text-slate-300">{me?.displayName}</span>
            <Button variant="secondary" onClick={() => logout.mutate()} loading={logout.isPending} title="Lock the application (log out)">
              Lock
            </Button>
          </div>
        </header>
        <main id="main" className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
