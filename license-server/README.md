# ToT Tools licence keys (test branch)

This branch uses key-only paid access. It has no email registration and no free tier.

## Key format

`TOT_FHSKW` is the example format. The server generates five random uppercase letters after `TOT_`. The example is not an actual key.

## Behaviour

- The app sends the entered key and a device identifier to the licence API.
- The API checks whether the key exists, is active and has not expired.
- First successful activation binds that key to one app/device.
- A different device is rejected until an admin uses **Unbind**.
- Admin can generate keys, revoke keys, inspect bindings and reset a binding.
- The full key is only returned once when generated. The database stores a peppered hash, not plaintext keys.

## Test backend deployment

The backend is a Cloudflare Worker with D1.

1. Install Node.js and Wrangler in `license-server`.
2. Create a D1 database and put its ID in `wrangler.toml`.
3. Set Worker secrets `ADMIN_TOKEN` and `LICENSE_PEPPER` to two different long random values.
4. Apply the D1 migration and deploy the Worker.
5. Set the GitHub Actions repository variable `TOT_LICENSE_API_URL` to the deployed Worker base URL, without a trailing slash.
6. Re-run **Build ToT Tools Licence Test APK** on this branch.

The admin dashboard is served at `/admin`. Keep its URL and admin token private. The Worker URL is used by the app and is not a secret.

## Security note

Five letters are convenient to type but provide only a small keyspace. They are acceptable for a controlled test with strict server-side rate limits. Use a longer random key (at least 10–12 characters after the prefix) before selling licences publicly. Never validate keys only inside the APK; all activation decisions must be made by the server.

This test setup does not process payments. Admin generates a key only after a payment has been confirmed through whatever sales channel is used. A production launch still needs secure deployment, monitoring, recovery procedures and a clear purchase/refund policy.
