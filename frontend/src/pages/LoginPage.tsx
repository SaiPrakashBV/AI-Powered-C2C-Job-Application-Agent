import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { useLogin } from '../auth/useAuth';
import { Alert, Button, Card, TextField } from '../components/ui';

export function LoginPage() {
  const login = useLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    login.mutate({ username, password });
  };
  const err = login.error instanceof ApiError ? login.error : null;

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-sm">
        <h1 className="mb-1 text-xl font-semibold">Sign in</h1>
        <p className="mb-4 text-sm text-slate-500">C2C Job Application Agent</p>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <TextField label="Username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
          <TextField label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          {login.error && <Alert>{err?.message ?? 'Login failed'}</Alert>}
          <Button type="submit" className="w-full" loading={login.isPending} disabled={!username || !password}>
            Sign in
          </Button>
        </form>
      </Card>
    </div>
  );
}
