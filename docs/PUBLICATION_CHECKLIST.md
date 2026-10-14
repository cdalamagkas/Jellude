# Public-launch checklist

Launch record: 14 October 2026. PR #2 was merged and the repository is now public. This document records observed settings; it does not itself change repository visibility.

## Owner decisions recorded

- GPL version 3 only for project code/artwork, with upstream third-party licenses preserved.
- Modified public distributions use distinct branding and do not imply maintainer endorsement; GPL copyright permissions are not restricted.
- Initial public launch distributes source only. Official signed APK releases come later.
- Use GitHub private vulnerability reporting.
- Keep the current Git author name/email and existing history; the author email will be public.
- Include the AI-development disclosure prominently. Do not claim independent audit or broad production readiness.

## Repository files prepared

- [x] GPLv3 LICENSE, NOTICE, dependency attribution and provenance.
- [x] Public-facing README, screenshots, build/use instructions, limitations, changelog.
- [x] Privacy/security policies and contribution instructions.
- [x] Bug/feature forms and PR template, with sensitive-data reminders.
- [x] Read-only CI permissions, non-persistent checkout credentials, actions pinned to full commit SHAs.
- [x] Weekly Dependabot configuration for Gradle and GitHub Actions (takes effect on the default branch).
- [x] Future release-signing/source-distribution checklist; APK upload removed from future CI runs.

## Checks before visibility changes

- Review the preparation PR and merge only after its checks pass.
- Review all reachable Git history and tracked files for credentials/private data, not just `.gitignore`. Automated pattern scans are useful but do not prove absence of secrets.
- Review screenshot content and historical documentation. The owner has accepted the existing author email; future commits keep that identity.
- Inspect previous Actions logs and artifacts. Older debug APK artifacts remain downloadable after a visibility change unless removed or expired. For a source-only launch, list those artifacts and obtain explicit confirmation before permanently deleting them; keep local development builds if wanted. Do not delete verification reports unnecessarily.
- Check that no GitHub Releases contain APKs or unrelated assets and that no signing secrets have been committed or exposed in logs.
- Inspect branch/tag refs, PR discussions and issue attachments that may become public.

## GitHub settings and visibility

These are service settings, not settings that Markdown can enable. Their availability depends on repository visibility and account plan. Record verified results here when they are actually applied.

| Setting/action | Status during private preparation |
|---|---|
| Public visibility | Verified public in GitHub after PR #2 merged |
| Private vulnerability reporting | Enabled and verified in public repository settings; use the private reporting link in SECURITY.md |
| Protect `main` with PRs and successful Android `build` check; block force pushes/deletion | Rule rechecked after publication; the private-plan non-enforcement warning is gone. Requires up-to-date branches/resolved conversations and disallows bypass; no second-person approval required. |
| Weekly dependency update PRs | Configuration merged to default branch; GitHub offers Edit dependabot.yml. Scheduled update results still require review. |
| Dependency graph, Dependabot alerts/security-fix PRs, malware alerts | Enabled and verified in repository settings during preparation; update PRs still need review |
| Secret scanning and push protection | Both enabled and verified after publication; supported-pattern protection does not replace manual review |
| Older CI APK artifacts | Owner approved permanent deletion of `Jellude-debug-2`, `Jellude-debug-3` and `Jellude-debug-4`; all three removed and their `verification-*` reports verified as retained. Local APKs and Git history were not changed. |

Do not purchase a plan, grant an integration more access, rewrite history, publish a binary, or change visibility merely to complete a checklist. Resolve any new material decision with the owner.

## Immediately after authorized publication

1. Enable private vulnerability reporting and verify **Security → Advisories → Report a vulnerability**. This is a launch gate, not merely a future aspiration.
2. Activate/verify main-branch protection and available dependency/secret controls, avoiding paid upgrades or unexpected access changes.
3. Verify the public README, license detection, images, issue forms, build instructions and absence of old public APK artifacts. Check the repository without relying on the maintainer's private access.
4. Update this status table to the actual verified settings. Keep official APK distribution disabled until [RELEASING.md](RELEASING.md) gates are met.

References: [visibility effects](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility), [private vulnerability reporting](https://docs.github.com/en/code-security/how-tos/report-and-fix-vulnerabilities/configure-vulnerability-reporting/configure-for-a-repository), [workflow security](https://docs.github.com/en/actions/reference/security/secure-use).
