# ToT Tools — Authentication Test

This branch contains a **local-only authentication simulator** for testing the sign-in screen and premium feature gating.

## Test accounts

| Role | Email | Password | Expected result |
|---|---|---|---|
| Premium | premium@tottools.test | PremiumTest!2026 | Editor and export enabled |
| Free | free@tottools.test | FreeTest!2026 | Save import works; editor/export locked |
| Expired | expired@tottools.test | ExpiredTest!2026 | Save import works; editor/export locked with expired message |

## Test checklist

1. Install the APK named **ToT Tools TEST**. It uses package ID `com.totemoflol.tottools.test`, so it can sit beside the normal app.
2. Sign in as Premium, import a Township save, and check that item editing and export are enabled.
3. Sign out, sign in as Free, import the same save, and check that the editor is locked and export is disabled.
4. Repeat with the Expired account and check the expiry message.
5. Close and reopen the app to confirm the test session persists. Sign out to return to the login screen.
6. To reset all local test state, clear the **ToT Tools TEST** app's storage.

## Important limitation

This simulator validates the UI, session persistence, and entitlement-based feature gating only. Credentials and entitlements are stored locally in the test build; anyone who inspects or modifies the APK can bypass them. **Do not use this code to collect real passwords, accept payments, or protect a paid release.**

Production authentication must validate credentials on a backend, and premium access must come from a server-verified entitlement or verified store purchase. Billing remains disabled in this test branch.
