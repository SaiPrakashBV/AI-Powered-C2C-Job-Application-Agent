import { useEffect } from 'react';
import type { Theme } from './api/types';

export function useApplyTheme(theme: Theme | undefined) {
  useEffect(() => {
    const t = theme ?? 'system';
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    const apply = () => {
      const dark = t === 'dark' || (t === 'system' && !!mq?.matches);
      document.documentElement.classList.toggle('dark', dark);
    };
    apply();
    mq?.addEventListener?.('change', apply);
    return () => mq?.removeEventListener?.('change', apply);
  }, [theme]);
}
