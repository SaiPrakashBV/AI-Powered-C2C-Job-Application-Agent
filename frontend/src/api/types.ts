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
  sourceDiscoveryMode: boolean;
  gmailBackfillDays: number;
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

export interface ModuleInfo { key: string; name: string; phase: number; availability: 'AVAILABLE' | 'PARTIAL' | 'PLANNED' }

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

// ---- Phase 2: Gmail, source rules, inbox ----
export type GmailAccountStatus = 'CONNECTED' | 'REAUTH_REQUIRED' | 'DISCONNECTED' | 'ERROR';
export interface GmailOAuthStatus { oauthConfigured: boolean; configSource: string | null; redirectUri: string; scopes: string[] }
export interface GmailAccount {
  id: string;
  emailAddress: string;
  status: GmailAccountStatus;
  syncEnabled: boolean;
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
  lastError: string | null;
  connectedAt: string | null;
  messagesSynced: number;
  backfillCompletedAt: string | null;
  historyId: number | null;
  grantedScopes: string | null;
}

export type SourceRuleType =
  | 'ALLOW_ADDRESS' | 'ALLOW_DOMAIN' | 'BLOCK_ADDRESS' | 'BLOCK_DOMAIN' | 'TRUSTED_FORWARDER' | 'ALLOW_LABEL' | 'BLOCK_LABEL';
export interface SourceRule {
  id: string;
  ruleType: SourceRuleType;
  matchValue: string;
  gmailAccountId: string | null;
  priority: number;
  enabled: boolean;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}
export type SourceDecision = 'ALLOWED' | 'BLOCKED' | 'DISCOVERY' | 'UNLISTED' | 'OWN_SENT';
export interface EvaluateResult { decision: SourceDecision; ruleId: string | null; explanation: string; continuesToClassification: boolean }
export interface ImportResult { created: number; skippedDuplicates: number; errors: string[] }

export type ProcessingStatus = 'PENDING_CLASSIFICATION' | 'EXCLUDED' | 'PARSE_ERROR' | 'NEEDS_REVIEW' | 'CLASSIFIED';
export interface EmailSummary {
  id: string;
  gmailAccountId: string;
  internalDate: string | null;
  fromAddress: string | null;
  fromName: string | null;
  subject: string | null;
  snippet: string | null;
  sourceDecision: SourceDecision;
  processingStatus: ProcessingStatus;
  hasWarnings: boolean;
}
export interface EmailAttachment {
  id: string;
  filename: string;
  contentType: string | null;
  sizeBytes: number;
  extractionStatus: 'EXTRACTED' | 'UNSUPPORTED' | 'TOO_LARGE' | 'FAILED' | 'NEEDS_REVIEW';
  extractionError: string | null;
  inline: boolean;
  downloadable: boolean;
  extractedChars: number;
}
export interface EmailDetail extends Omit<EmailSummary, 'snippet' | 'hasWarnings'> {
  gmailMessageId: string;
  gmailThreadId: string | null;
  replyTo: string | null;
  to: string | null;
  cc: string | null;
  messageId: string | null;
  inReplyTo: string | null;
  labels: string | null;
  sourceExplanation: string | null;
  textBody: string | null;
  htmlText: string | null;
  parseWarnings: string | null;
  attachments: EmailAttachment[];
}
