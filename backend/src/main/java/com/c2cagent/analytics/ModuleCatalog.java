package com.c2cagent.analytics;

import java.util.List;

/**
 * Honest status of each functional area, shown in the UI so unfinished features are never presented as
 * working. Update the status here when a phase is completed and verified (see PROGRESS.md).
 */
public final class ModuleCatalog {
    private ModuleCatalog() {}

    public enum Availability { AVAILABLE, PARTIAL, PLANNED }

    public record ModuleInfo(String key, String name, int phase, Availability availability) {}

    public static final List<ModuleInfo> MODULES = List.of(
            new ModuleInfo("dashboard", "Main Dashboard", 1, Availability.AVAILABLE),
            new ModuleInfo("settings", "Settings, Backups & Diagnostics", 1, Availability.AVAILABLE),
            new ModuleInfo("gmail", "Gmail Accounts", 2, Availability.AVAILABLE),
            new ModuleInfo("source-rules", "Email Source & Vendor Rules", 2, Availability.AVAILABLE),
            new ModuleInfo("inbox", "Inbox / Classification Review", 3, Availability.PARTIAL),
            new ModuleInfo("jobs", "Extracted Job Details", 3, Availability.PLANNED),
            new ModuleInfo("profiles", "Job Search Profiles", 4, Availability.PLANNED),
            new ModuleInfo("resumes", "Resume Library", 5, Availability.PLANNED),
            new ModuleInfo("master-profile", "Master Professional Profile", 5, Availability.PLANNED),
            new ModuleInfo("matching", "Relevance & ATS Comparison", 5, Availability.PLANNED),
            new ModuleInfo("ai-providers", "AI Provider Settings", 6, Availability.PLANNED),
            new ModuleInfo("tailoring", "AI Tailoring Workspace", 7, Availability.PLANNED),
            new ModuleInfo("approvals", "Application Approval Queue", 8, Availability.PLANNED),
            new ModuleInfo("composer", "Email Composer", 8, Availability.PLANNED),
            new ModuleInfo("applications", "Submitted Applications", 8, Availability.PLANNED),
            new ModuleInfo("replies", "Vendor Replies & Interviews", 8, Availability.PLANNED),
            new ModuleInfo("feedback", "Feedback & Decision History", 9, Availability.PLANNED),
            new ModuleInfo("automation", "Automation Policy", 9, Availability.PLANNED),
            new ModuleInfo("reports", "Reports & Analytics", 9, Availability.PLANNED));
}
