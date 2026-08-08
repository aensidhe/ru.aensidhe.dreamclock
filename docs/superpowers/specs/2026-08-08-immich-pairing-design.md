# Immich QR / Local-Network Pairing — Design

This is Plan 5 of the Immich photos feature. Plan 4 gave the screensaver a D-pad
settings surface where the Immich host and API key are typed by hand and stored
encrypted. Typing a long API key on a TV remote is the friction this plan
removes: the owner points a phone at a QR code on the TV, enters the credential
on the phone, and the TV receives it over the local network. No cloud relay is
involved — the "server in the middle" is the TV itself.

## Goal

Let a phone hand Immich credentials to the TV over the LAN, without typing them
on the remote and without any traffic leaving the network. Two phone-side modes:
paste an existing API key, or enter Immich email and password so the TV mints a
least-privilege read-only key. Manual entry from Plan 4 stays as the always-there
baseline.

## Scope

In scope:

- A TV-side pairing HTTP server (Ktor CIO, embedded) that serves a phone page and
  accepts one encrypted credential payload, then shuts down.
- A phone page (served inline) with the two modes, encrypting its payload with a
  key delivered out-of-band through the QR code.
- QR generation (ZXing) of the pairing URL.
- Selection of which network interface/address the TV listens on and advertises,
  surfaced in the Immich settings section.
- The Immich mint calls (`login`, `create-api-key`) and paste-key validation,
  saving through the existing Keystore-backed `CredentialsStore`.
- Ru/En localization for every new label and status string.

Not in scope:

- Video and audio (Plan 6).
- Any pairing transport other than a phone browser on the same LAN. Immich OAuth
  is authorization-code with redirect and has no device grant, so it is not a
  TV-friendly path; accounts it would need are covered by manual entry.

## Architecture

Pure, device-independent logic lives in `:core` and is unit-tested on the JVM.
Everything Android, network, or UI lives in `:app`.

`:core`:

- `PairingCrypto` — AES-GCM encrypt/decrypt of the payload given the 256-bit key
  and a 96-bit IV (`javax.crypto`, runs on plain JVM).
- `PairingPayload` — encode/decode the phone→TV JSON (`{mode, host, apiKey}` or
  `{mode, host, email, password}`) and the wire envelope `{iv, ciphertext}`.
- `PairingUrl` — build and parse `http://<host>:<port>/#k=<base64url>`, bracketing
  IPv6 literals (`http://[fd00::1]:<port>/…`) and leaving IPv4 bare.
- `InterfaceSelection` — a pure function over a supplied list of interface/address
  records: filter, ordering, default pick, and resolve-by-(name + family) with
  fallback. Takes plain data, so it needs no real network stack to test.

`:app`:

- `PairingServer` (Ktor CIO) — `GET /` serves the phone page; `POST /pair` reads
  the envelope, decrypts via `PairingCrypto`, and hands the payload to the
  controller. Bound to the selected address on an ephemeral port; lifecycle-scoped
  to the pairing screen.
- `PairingController` — owns the server, the in-memory key, and the status state.
  On a decrypted payload it validates the paste-key (one cheap authed call) or
  runs the login→mint sequence, saves the key through `CredentialsStore`, then
  stops the server and drops the key.
- `LanInterfaces` — enumerate `NetworkInterface` addresses into the records
  `InterfaceSelection` consumes.
- `QrImage` — ZXing encode of the pairing URL to a bitmap for Compose.
- `PairingScreen` — full-screen Compose: the QR, a one-line scan instruction, a
  live status line, a countdown, and Cancel.
- The inline phone page asset (HTML + JS, including a vendored AES-GCM routine).
- Immich service additions: `login` and `create-api-key` endpoints.

## Pairing flow, lifecycle, security

1. The owner picks "Pair with phone" in the Immich settings section, which opens
   `PairingScreen`.
2. The TV resolves the selected interface to its current address, generates a
   fresh 256-bit AES-GCM key (SecureRandom, held only in memory), starts
   `PairingServer` bound to that address on an ephemeral port, and renders the QR
   of `http://<address>:<port>/#k=<base64url key>` with a one-line
   "Scan with your phone's camera."
3. The phone loads the page over plain HTTP. The key rides in the URL fragment,
   which the browser never sends to the server; the page reads it from
   `location.hash`.
4. On load, before anything else, the page rewrites its own history entry
   (`history.replaceState(null, "", location.pathname)`) so the key is not
   retained in the address bar or the back/forward entry.
5. The owner enters the credential on the phone. The page generates a random
   96-bit IV, AES-GCM-encrypts the payload with the fragment key, and POSTs
   `{iv, ciphertext}` (base64url) to `/pair`. Plain HTTP is safe because an
   eavesdropper never had the fragment key and sees only ciphertext.
6. The TV decrypts and verifies the GCM tag, then:
   - paste-key mode: validate the key with one cheap authenticated call, then
     save it;
   - email/password mode: `POST /api/auth/login`, then `POST /api/api-keys` with
     `permissions: [asset.read, asset.view, asset.download]`, save that key, and
     discard the password — it is used only for those two calls and never
     persisted or echoed back.
7. On success the server stops, the key is dropped, the screen confirms and
   auto-closes to settings, whose status line now reflects the saved-credential
   health. A GCM-tag or decrypt failure is rejected and the server keeps
   listening for a retry (an attacker cannot forge a valid payload without the
   key; failures are the owner mistyping in a stale tab).

Lifetime: the server exists only while `PairingScreen` is on screen. The screen
shows a countdown ("Expires in 4:32"); at zero, and on Cancel, the server stops
and the key is dropped immediately. The key is single-use, so any copy that
survived in browser history is inert once the session ends.

## Interface selection

The QR must advertise a concrete address the phone can route to, and the server
binds to that same address (we listen only where we advertise).

Candidates: every address on interfaces where
`isUp && !isLoopback && !isLinkLocalAddress()`. Link-local is excluded outright,
both families: IPv4 `169.254.*` (APIPA) has no real connectivity, and IPv6
`fe80::` needs a zone/scope id that is meaningless to a phone on its own scope, so
neither is reliably reachable across devices.

Ordering (top = default), so a usable private address wins:

1. IPv4 site-local — RFC 1918 (`isSiteLocalAddress()`).
2. IPv4 global.
3. IPv6 ULA — `fc00::/7`, detected by a leading `0xfc`/`0xfd` byte, since Java's
   `isSiteLocalAddress()` only recognizes the deprecated IPv6 `fec0::/10`.
4. IPv6 global.

A single interface can expose both a qualifying IPv4 and IPv6, so the list is
per-address, not per-interface. Each row shows the interface name and the current
address (`eth0 — 192.168.1.42`, `wlan0 — fd00::1a2b`).

Persistence: the choice is stored as interface name plus address family, which
survives a DHCP lease change. At pairing time the current best address of that
family on that interface is resolved; if the interface has no qualifying address
of that family then, the selection falls back to the top of the current list, and
the screen shows which address is actually in use.

Enumeration uses `java.net.NetworkInterface`, which needs no Android permission;
binding the server needs only `INTERNET`, already declared.

## Phone page and crypto

The page is served inline and is fully self-contained — no CDN, since the phone
may have no route to the internet through the TV.

`crypto.subtle` (WebCrypto) is only available in a secure context: HTTPS, or
`localhost`/`127.0.0.1`. A LAN URL like `http://192.168.1.42:<port>/` is not a
secure context, so `crypto.subtle` is undefined on the phone. Rather than serve
HTTPS (a self-signed cert triggers a browser warning right after the scan) or
drop encryption (the API key or password would cross the LAN in cleartext), the
page carries a vendored AES-GCM implementation inline. It runs in any phone
browser over plain HTTP, and both sides speak the same AES-GCM so the TV's
`PairingCrypto` decrypts it directly.

The vendored routine is the AES-GCM part of `@noble/ciphers` (Paul Miller;
audited, MIT), at a pinned upstream version, checked into the repo as an app
asset served inline by `GET /`, with its license header retained and its version
and provenance recorded. GCM is not hand-rolled. The fixed cross-implementation
test vector proves the vendored JS and `javax.crypto` agree byte-for-byte.

Wire format: the payload is UTF-8 JSON; the envelope is
`{ "iv": base64url(12 bytes), "ciphertext": base64url(GCM output incl. tag) }`.
The IV is a random 96-bit nonce, the recommended GCM size (NIST SP 800-38D).
The 256-bit key is carried in the URL fragment as base64url and imported by both
the JS routine and `PairingCrypto`.

The page shows two tabs: paste-key (host + key) and email/password
(host + email + password). It POSTs once, shows a spinner while the TV validates
or mints, and renders the TV's verdict.

## TV UI

The Immich settings section gains:

- The interface selector: a focusable row that expands the sorted per-address
  list, each labelled name plus current address, persisted by name + family.
- A "Pair with phone" action that opens `PairingScreen`.

`PairingScreen` (full-screen, D-pad):

- The QR, a one-line scan instruction, and a live status line
  (waiting → received → validating/minting → success/failure), reusing the
  existing copyable-diagnostic pattern for errors.
- A countdown showing the remaining window.
- A Cancel control that stops the server and drops the key. On success the screen
  confirms and auto-closes to settings.

All labels are localized Ru/En via the existing settings-label mechanism.

## Immich mint calls and permissions

- Paste-key mode validates with one cheap authenticated call before saving.
- Email/password mode calls `POST /api/auth/login`, then `POST /api/api-keys`
  requesting `asset.read`, `asset.view`, `asset.download`. A normal logged-in
  session can create an API key (`apiKey.create`). The minted key is
  least-privilege — read-only — unlike a hand-made all-permissions key.

## Testing

Hermetic (`:core` and `:app` with OkHttp `MockWebServer`, no live server, no
browser):

- `PairingCrypto`: AES-GCM round-trip, tamper / tag-failure rejection, a fixed
  cross-implementation vector matching the bundled JS output, and a NIST
  known-answer test. The fixed vector is how JS↔JVM interop is guarded in CI
  without running a browser.
- `PairingPayload` and `PairingUrl`: encode/decode both modes and the envelope;
  URL build/parse with IPv6 bracketing and fragment handling.
- `InterfaceSelection`: over a synthetic record list — the filter, the ordering
  (v4 site-local → v4 global → v6 ULA → v6 global), the default pick, and the
  persist-by-(name + family) resolve with fallback.
- Mint path: `login` and `create-api-key` request construction and response
  parsing against `MockWebServer`, asserting the requested permissions.
  Paste-key validation call likewise.

On-device manual (no adb on the target hardware): a real phone scans the QR, the
page loads over plain HTTP, both modes complete end to end against the live
Immich server, a successful pairing saves the key and the photo deck starts, and
the interface selector is sanity-checked on both an Ethernet Shield and a WiFi
stick.

## Open questions and risks

- Vendored AES-GCM: the pinned `@noble/ciphers` routine must exactly match
  `PairingCrypto`. The fixed cross-implementation test vector pins this; a version
  bump of the vendored asset re-runs that vector before it is trusted.
- Login constraints: the mint path assumes a local password without TOTP
  two-factor and not an OAuth-only account. Manual entry and paste-key cover the
  rest.
- Phone browser variance: the QR-scanner's in-app browser must run the inline JS
  and allow the plain-HTTP POST back to the TV. Worth confirming on the owner's
  actual phone during on-device validation.
