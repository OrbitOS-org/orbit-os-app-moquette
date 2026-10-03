<p align="center">
  <img src="https://www.orbit-os.org/images/vscode/orbit-os-logo.png" width="300" alt="Orbit OS">
</p>

<h1 align="center">Moquette MQTT Broker for Orbit OS</h1>

<p align="center"><b>Turn your Orbit OS device into an MQTT broker — configured, started and monitored from a web admin page.</b></p>

An [Orbit OS](https://www.orbit-os.org/?ref=github-moquette) app that embeds the [Moquette](https://github.com/moquette-io/moquette) MQTT broker (v0.17) and adds a web admin UI in the Orbit OS AppHub: start/stop the broker, see connected clients and live activity, edit every setting and manage MQTT users — no config files, no SSH.

Runs on Raspberry Pi, Arduino UNO Q and other ARM64 devices with Orbit OS (free Community Edition).

## Features

- **Full MQTT broker** — MQTT 3.1.1 and 5, QoS 0/1/2, optional MQTT over WebSocket, persistence (sessions and queued messages survive restarts)
- **Off by default** — the broker only starts when you turn it on, so installing the app never grabs a port silently
- **Live dashboard** — status, uptime, connected clients (with *kick*), rolling feed of connect / publish / disconnect events
- **Configuration from the browser** — bind address, MQTT and WebSocket ports, persistence, anonymous access, ACL file, session queue size, client expiration; invalid settings are rejected without stopping a running broker
- **MQTT users** — add or remove username/password logins for clients; the app refuses to start with anonymous access off and no users
- **Password-protected admin page**, reached only through the Orbit OS AppHub
- **About page** with the exact Moquette broker version and app version running on your device
- Broker events forwarded to the Orbit OS log stream

| Setting | Default |
|---|---|
| Start automatically | off |
| MQTT port | 1883 (`0.0.0.0`) |
| WebSocket | off (port 8095 when enabled) |
| Persistence | on |
| Anonymous access | allowed |

## Install

**From the Orbit OS Store (recommended):** install [Moquette](https://store.orbit-os.org/app/moquette-jar?ref=github-moquette) on your device in one click.

<a href="https://store.orbit-os.org/app/moquette-jar?ref=github-moquette"><img src="https://www.orbit-os.org/images/badges/get-it-on-orbit-os-store@3x.png" width="200" alt="Get it on Orbit OS Store"></a>

**From source — recommended: [Orbit Studio](https://marketplace.visualstudio.com/items?itemName=orbit-os.orbit-studio) (VS Code):**

You need [VS Code](https://code.visualstudio.com/) with the Orbit Studio extension and a **Java 17 JDK** installed (for example [Eclipse Temurin 17](https://adoptium.net/); `java` on your PATH).

1. Clone the repository and open the folder in VS Code with the Orbit Studio extension:
   ```bash
   git clone https://github.com/OrbitOS-org/orbit-os-app-moquette
   code orbit-os-app-moquette
   ```
2. In the Orbit sidebar, set your device's IP.
3. Use **Run** to try it live against a device in Developer Mode, then **Build + Deploy** to install the signed `.orb`. Orbit Studio downloads the Orbit OS Java SDK into `orbit-os-sdk-java/` the first time (or run **Orbit: Add / Update SDK**).

**Without Orbit Studio:** clone the [Orbit OS Java SDK](https://github.com/OrbitOS-org/orbit-os-sdk-java) into the project folder, then build the JAR (Java 17) — use Orbit Studio to package and sign the `.orb`:

```bash
git clone --branch v26.0.3 --depth 1 https://github.com/OrbitOS-org/orbit-os-sdk-java
./gradlew :apps:moquette:shadowJar
```

## Getting started

1. Open **Moquette** from the AppHub on your device.
2. Log in with **`admin` / `admin`** and **change the password** in the *Account* card straight away.
3. Review the settings (ports, persistence, authentication) and press **Start broker** — or tick *Start automatically* and save.
4. Point your MQTT clients (Home Assistant, Node-RED, sensors…) at `<DEVICE_IP>:1883`.

## Development (Orbit Studio)

This project follows the [Orbit Studio](https://marketplace.visualstudio.com/items?itemName=orbit-os.orbit-studio) Java layout:

| Path | What |
|---|---|
| `apps/moquette/` | app source — `App.java` (startup), `broker/` (embedded Moquette, config, MQTT users, event bridge), `admin/` (admin HTTP server + auth), `src/main/resources/admin/` (web UI), `metadata.json` (manifest & permissions) |
| `apps/moquette/orb/icon.svg` | launcher / Store icon |
| `orbit-os-sdk-java/` | [Orbit OS Java SDK](https://github.com/OrbitOS-org/orbit-os-sdk-java) v26.0.3 — not in the repository; added by Orbit Studio (git-ignored) |
| `orbit.project.json` | Orbit Studio project settings (device IP and signing paths go in the git-ignored `orbit.project.local.json`) |

- **Recommended workflow:** open the folder in VS Code with Orbit Studio, then **Run** against a device in Developer Mode, or **Build + Deploy**.
- Development TLS certificates live in `certs/grpc/` and are never committed; runtime data (`apps/moquette/config/`, `apps/moquette/data/`) is git-ignored.

## Security

- **Change the default `admin` / `admin` admin password** on first login.
- The admin page is only reachable through the Orbit OS AppHub (it listens on loopback), behind the Launcher login.
- The broker allows **anonymous MQTT connections by default** and MQTT is plain TCP (no TLS in the UI yet). Before exposing it beyond a trusted network, turn anonymous access off and add MQTT users.
- To reach the broker from other machines, the MQTT/WebSocket ports must be allowed by the device firewall (Settings → Firewall).

## Acknowledgments

Thanks to **[Andrea Selva](https://github.com/andsel)** and the Moquette contributors for the **[Moquette](https://github.com/moquette-io/moquette)** project this app is built on — an open-source, lightweight Java MQTT broker (Apache-2.0). This app does not modify Moquette; it embeds the official `io.moquette:moquette-broker` release and adds the Orbit OS integration and the admin UI around it.

If you find the broker useful, give the [Moquette repository](https://github.com/moquette-io/moquette) a star.

Third-party components bundled in the package are listed in [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES).

## Links

[App in the Store](https://store.orbit-os.org/app/moquette-jar?ref=github-moquette) · [Orbit OS](https://www.orbit-os.org/?ref=github-moquette) · [Getting started](https://www.orbit-os.org/getting_started.html?ref=github-moquette) · [SDK reference](https://www.orbit-os.org/api-reference.html?ref=github-moquette) · [Forum](https://forum.orbit-os.org/?ref=github-moquette) · info@orbit-os.org

## License

Apache-2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
