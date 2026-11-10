export type UserRole = 'OWNER' | 'MEMBER';
export type AutomationMode = 'MANUAL_APPROVAL' | 'CONDITIONAL' | 'RULES_BASED' | 'FULL_AUTONOMOUS';
export type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'DEAD' | 'CANCELLED';
export type Theme = 'system' | 'light' | 'dark';

export interface SetupStatus { setupRequired: boolean; demoMode: boolean }

export interface Me {
  userId: string;
  workspaceId: string;
  username: string;
  displayName: string;
  role: UserRole;
  sessionTimeoutMinutes: number;
}

export interface Settings {
  automationMode: AutomationMode;
  emergencyStop: boolean;
  outboundDryRun: boolean;
  sessionTimeoutMinutes: number;
  gmailPollingIntervalMinutes: number;
  externalAiConsent: boolean;
  theme: Theme;
  version: number;
  updatedAt: string;
}

export interface AuditEvent {
  id: string;
  actorType: string;
  eventType: string;
  entityType: string | null;
  entityId: string | null;
  summary: string;
  details: string | null;
  createdAt: string;
}

export interface ModuleInfo { key: string; name: string; phase: number; availability: 'AVAILABLE' | 'PLANNED' }

export interface DashboardSummary {
  displayName: string;
  safety: { automationMode: AutomationMode; emergencyStop: boolean; outboundDryRun: boolean; externalAiConsent: boolean };
  system: { database: string; schemaVersion: string | null; demoMode: boolean };
  jobCounts: Record<JobStatus, number>;
  recentActivity: AuditEvent[];
  modules: ModuleInfo[];
}

export interface Job {
  id: string;
  jobType: string;
  status: JobStatus;
  attempts: number;
  maxAttempts: number;
  runAfter: string;
  lastError: string | null;
  failureKind: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface Diagnostics {
  javaVersion: string;
  os: string;
  databaseVersion: string;
  schemaVersion: string;
  dataDirectory: string;
  dataDirectoryWritable: boolean;
  demoMode: boolean;
  jobWorkerEnabled: boolean;
  maxMemoryMb: number;
}
