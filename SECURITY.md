# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for a sensitive security problem. Use
GitHub's **private vulnerability reporting**: go to the repository's
**Security → Report a vulnerability** tab and open a private report. This is the
preferred channel and is only visible to the maintainer.

For non-sensitive problems (for example a crash, a wrong API call, or anything
that does not put users at risk), a normal public issue is fine.

Please give a reasonable amount of detail to reproduce the issue, and don't
publish sensitive exploit details publicly until there has been a chance to
address them.

## What Mealio stores

- The Mealie **API token is stored using the Android Keystore** (AES/GCM). It is
  never written in plain text, and app data is excluded from cloud backup and
  device transfer.
- Mealio **never** needs your Mealie password — only a long-lived API token.

## Reporting secrets

**Never** paste your Mealie API token, keystore passwords, or any other secret
into a public issue, pull request, or screenshot. If you did so by accident,
revoke and regenerate the token in Mealie immediately.

## Network security

- Mealio talks to your Mealie server directly over HTTP/JSON. **HTTP does not
  provide TLS** — an `http://` connection is unencrypted.
- Plain `http://` is acceptable **only inside a trusted, private, already
  encrypted network**, such as a home LAN or a **Tailscale** network (traffic
  runs inside the encrypted Tailscale tunnel).
- For any server reachable from an untrusted or public network, use **HTTPS**.

## Supported versions

This is a small personal project; security fixes target the **latest release**.
