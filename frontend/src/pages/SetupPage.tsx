import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { useSetup } from '../auth/useAuth';
import { Alert, Button, Card, TextField } from '../components/ui';

export function SetupPage() {
  const setup = useSetup();
  const [form, setForm] = useState({ username: '', displayName: '', email: '', password: '', confirm: '' });
  const [localError, setLocalError] = useState<string | null>(null);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (form.password !== form.confirm) {
      setLocalError('Passwords do not match');
      return;
    }
    setup.mutate({
      username: form.username,
      displayName: form.displayName,
      email: form.email || undefined,
      password: form.password,
    });
  };
  const err = setup.error instanceof ApiError ? setup.error : null;
  const fieldError = (f: string) => err?.fieldErrors.find((x) => x.field === f)?.message;

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <h1 className="mb-1 text-xl font-semibold">Welcome — first-time setup</h1>
        <p className="mb-4 text-sm text-slate-500">
          Create the local owner account. Your data stays on this computer. Gmail is connected later through Google's
          sign-in page; your Gmail password is never requested.
        </p>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <TextField label="Username" autoComplete="username" value={form.username} onChange={set('username')} error={fieldError('username')} required autoFocus />
          <TextField label="Display name" value={form.displayName} onChange={set('displayName')} error={fieldError('displayName')} required />
          <TextField label="Email (optional)" type="email" value={form.email} onChange={set('email')} error={fieldError('email')} />
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            hint="At least 12 characters, with letters and a digit or symbol."
            required
          />
          <TextField label="Confirm password" type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} required />
          {(localError || setup.error) && <Alert>{localError ?? err?.message ?? 'Setup failed'}</Alert>}
          <Button type="submit" className="w-full" loading={setup.isPending}>
            Create account
          </Button>
        </form>
      </Card>
    </div>
  );
}
