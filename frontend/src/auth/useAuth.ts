import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api, ApiError, resetCsrf } from '../api/client';
import type { Me, SetupStatus } from '../api/types';

/**
 * Drops all cached data except the auth queries. (QueryClient.clear() would detach mounted observers, so a
 * following setQueryData would never re-render the app.)
 */
export function dropUserData(qc: QueryClient) {
  qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' && q.queryKey[0] !== 'setup-status' });
}

export function useSetupStatus() {
  return useQuery({ queryKey: ['setup-status'], queryFn: () => api<SetupStatus>('/api/v1/auth/setup-status') });
}

/** Current user, or null when not logged in (401). Other errors surface normally. */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await api<Me>('/api/v1/auth/me');
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { username: string; password: string }) => api<Me>('/api/v1/auth/login', { method: 'POST', json: v }),
    onSuccess: (me) => {
      resetCsrf(); // session id rotated on login
      dropUserData(qc);
      qc.setQueryData(['me'], me);
    },
  });
}

export function useSetup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { username: string; displayName: string; email?: string; password: string; workspaceName?: string }) =>
      api<Me>('/api/v1/auth/setup', { method: 'POST', json: v }),
    onSuccess: (me) => {
      resetCsrf();
      dropUserData(qc);
      qc.setQueryData(['me'], me);
      qc.setQueryData(['setup-status'], { setupRequired: false, demoMode: false });
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('/api/v1/auth/logout', { method: 'POST' }),
    onSettled: () => {
      resetCsrf();
      dropUserData(qc);
      qc.setQueryData(['me'], null);
    },
  });
}
