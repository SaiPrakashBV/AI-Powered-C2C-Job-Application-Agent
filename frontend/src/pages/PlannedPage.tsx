import { useLocation } from 'react-router-dom';
import { Card } from '../components/ui';
import { ALL_NAV_ITEMS } from '../navigation';

/** Shown for screens whose module has not been built yet — never a fake/demo UI. */
export function PlannedPage() {
  const { pathname } = useLocation();
  const item = ALL_NAV_ITEMS.find((n) => n.path === pathname);
  return (
    <Card title={item?.label ?? 'Not found'}>
      {item ? (
        <p className="text-sm text-slate-600 dark:text-slate-300">
          This screen is scheduled for <strong>Phase {item.phase}</strong> and is not implemented yet. See PROGRESS.md
          in the repository for the current status.
        </p>
      ) : (
        <p className="text-sm">The page you requested does not exist.</p>
      )}
    </Card>
  );
}
