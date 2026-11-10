/**
 * `phase` = phase in which the screen became usable (1 = no phase tag shown). Inbox shows synced mail since
 * Phase 2; its classification review arrives in Phase 3.
 */
/** Sidebar entries for all 20 screens in the spec. `moduleKey` links to the backend ModuleCatalog availability. */
export interface NavItem { path: string; label: string; moduleKey: string; phase: number }

export const NAV_SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: 'Overview',
    items: [
      { path: '/', label: 'Dashboard', moduleKey: 'dashboard', phase: 1 },
      { path: '/reports', label: 'Reports & Analytics', moduleKey: 'reports', phase: 9 },
    ],
  },
  {
    title: 'Inbox',
    items: [
      { path: '/gmail', label: 'Gmail Accounts', moduleKey: 'gmail', phase: 1 },
      { path: '/inbox', label: 'Inbox & Classification', moduleKey: 'inbox', phase: 2 },
      { path: '/source-rules', label: 'Source & Vendor Rules', moduleKey: 'source-rules', phase: 1 },
      { path: '/jobs', label: 'Extracted Jobs', moduleKey: 'jobs', phase: 3 },
    ],
  },
  {
    title: 'Matching',
    items: [
      { path: '/profiles', label: 'Job Search Profiles', moduleKey: 'profiles', phase: 4 },
      { path: '/resumes', label: 'Resume Library', moduleKey: 'resumes', phase: 5 },
      { path: '/master-profile', label: 'Master Profile', moduleKey: 'master-profile', phase: 5 },
      { path: '/matching', label: 'Relevance & ATS', moduleKey: 'matching', phase: 5 },
      { path: '/tailoring', label: 'AI Tailoring', moduleKey: 'tailoring', phase: 7 },
    ],
  },
  {
    title: 'Applications',
    items: [
      { path: '/approvals', label: 'Approval Queue', moduleKey: 'approvals', phase: 8 },
      { path: '/composer', label: 'Email Composer', moduleKey: 'composer', phase: 8 },
      { path: '/applications', label: 'Submitted', moduleKey: 'applications', phase: 8 },
      { path: '/replies', label: 'Replies & Interviews', moduleKey: 'replies', phase: 8 },
      { path: '/feedback', label: 'Feedback & History', moduleKey: 'feedback', phase: 9 },
    ],
  },
  {
    title: 'Configuration',
    items: [
      { path: '/ai-providers', label: 'AI Providers', moduleKey: 'ai-providers', phase: 6 },
      { path: '/automation', label: 'Automation Policy', moduleKey: 'automation', phase: 9 },
      { path: '/settings', label: 'Settings & Diagnostics', moduleKey: 'settings', phase: 1 },
    ],
  },
];

export const ALL_NAV_ITEMS = NAV_SECTIONS.flatMap((s) => s.items);
