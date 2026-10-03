# Moquette — MQTT Broker for OrbitOS

Turn your OrbitOS device into a lightweight MQTT broker, with a clean web
dashboard to manage it — no config files to edit by hand, no SSH required.

## What it does

- Runs a full MQTT broker (MQTT 3.x and 5, QoS 0/1/2) directly on your
  device, powered by [Moquette](https://github.com/moquette-io/moquette)
  **v0.17**, the open-source Java MQTT broker.
- Ships with a built-in admin web page: live status, connected clients, a
  real-time activity feed, and a complete settings editor — all from your
  browser.
- **Off by default.** The broker never starts on its own until you turn it
  on from the admin page, so installing the app can't silently grab a port
  you needed for something else.

## Key features

- **One-tap start/stop**, plus a "start automatically" switch if you want
  the broker running whenever the app runs.
- **Live dashboard** — see who's connected (with a "kick" button to
  disconnect a client), and a rolling feed of connect / publish /
  disconnect events as they happen.
- **Full configuration from the browser**, no file editing required:
  - Bind address and MQTT port
  - Optional MQTT-over-WebSocket listener
  - Persistence (sessions and queued messages survive restarts) and data
    directory
  - Anonymous access on/off, ACL file support, session queue size,
    persistent client expiration
- **MQTT user management** — add or remove username/password logins for your
  MQTT clients (separate from the admin login) directly from the page. No
  password files to hand-craft — add a user, and it's ready to use.
- **Password-protected admin page** — the broker's controls aren't open to
  anyone who finds the URL.
- **About page** showing the exact Moquette broker version and app version
  running on your device.

## Getting started

1. Install and open the app.
2. Open its admin page from the OrbitOS portal.
3. **Log in.** The admin page ships with default credentials —
   **username `admin`, password `admin`.** Change them straight away from
   the Account section, before doing anything else, especially before you
   plan to reach the broker from outside your own network.
4. Review the settings (ports, persistence, authentication) and press
   **Start broker** — or turn on "Start automatically" and save, so it
   comes up on its own the next time the app runs.

## Security notes

- Default admin login is **`admin` / `admin`** — change it on first login.
- The broker allows anonymous MQTT connections by default. If you turn that
  off, you must also add at least one MQTT user, or no client — including
  you — will be able to connect; the app blocks that combination for you.
- The admin web page itself is reached only through the OrbitOS portal,
  never exposed directly on the network.

## Requirements

- An OrbitOS device with app-hub support.
- Permissions used: `SystemService`, `AppHubService`.

## Acknowledgments

Thanks to Andrea Selva for the [Moquette](https://github.com/moquette-io/moquette)
project this app is built on.

