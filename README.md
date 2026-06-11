# Surface Map – Burp Suite Extension

Passively maps the functional attack surface of a web application as you browse it through Burp's proxy. Captures every in-scope endpoint, groups them by function (Authentication, Account, Money movement, Commerce, Data/API, Admin), tracks parameters across the surface, and renders a pannable/zoomable visual map with full request/response viewer and PNG export.

## Features

- **Passive capture** – records endpoints as you browse; no active scanning
- **Scope-filtered** – only in-scope traffic (via Target → Scope) is recorded
- **Functional grouping** – keyword-driven categorisation (editable in `HtmlRenderer.java`)
- **NEW highlighting** – endpoints first seen since the last baseline are visually flagged
- **Parameter analysis** – click any parameter to highlight every endpoint that uses it
- **Request/response viewer** – full raw HTTP for every captured endpoint, with copy buttons
- **Project import/export** – save and reload `.json` project files per engagement
- **PNG export** – full-resolution diagram download from the browser map

## Requirements

- Burp Suite 2022.9.5+ (Montoya API)
- Java 17 JDK (for building)
- Gradle 8.x (for building)

## Build

```bash
./gradlew build
```

The output JAR is at `build/libs/surface-map.jar`.

## Install in Burp

1. Open Burp Suite.
2. Go to **Extensions → Installed → Add**.
3. Set **Extension type** to **Java**.
4. Select `build/libs/surface-map.jar`.
5. Click **Next** – you should see "Surface Map loaded." in the Output tab and a new **Surface Map** tab in Burp.

## Usage

1. Define your target in **Target → Scope**.
2. In the **Surface Map** tab, tick **Capture (proxy, in-scope)**.
3. Browse the target through Burp's proxy, opening every menu, tab, and screen.
4. Click **Open map in browser** to view the visual map.
5. Click **Set baseline** when you want the current state to be the comparison point for future sessions.

## Submission checklist (BApp Store acceptance criteria)

| Criterion | Status |
|---|---|
| Unique function | ✅ No existing BApp maps functional surface with param analysis |
| Clear name | ✅ "Surface Map" |
| Operates securely | ✅ HTTP messages treated as untrusted; UI data not auto-filled from traffic |
| Includes all dependencies | ✅ No runtime deps beyond Montoya API (provided by Burp) |
| Uses threads | ✅ Handler returns immediately; model/disk work on background executor |
| Unloads cleanly | ✅ `registerUnloadingHandler` shuts down both executor services |
| Uses Montoya API | ✅ `net.portswigger.burp.extensions:montoya-api` via Gradle |
| GUI parenting | ✅ All dialogs parented to `SwingUtils.suiteFrame()` |
| Large project safety | ✅ Request/response bytes snapshotted before handler returns; capped at 250 KB |
| Offline working | ✅ No external network calls; all processing is local |

## Project structure

```
surface-map/
├── build.gradle
├── settings.gradle
├── README.md
└── src/main/java/io/github/surfacemap/
    ├── SurfaceMapExtension.java   # BurpExtension entry point + unload handler
    ├── SurfaceMapHandler.java     # HttpHandler (passive, proxy+scope filtered)
    ├── SurfaceMapModel.java       # Thread-safe data model + persistence
    ├── SurfaceMapTab.java         # Swing UI tab
    └── HtmlRenderer.java          # Builds the self-contained HTML map
```

## Submitting to the BApp Store

1. Push this repo to GitHub (public).
2. Confirm it meets all acceptance criteria above.
3. Open a **New extension submission** issue on [PortSwigger/extension-portal](https://github.com/PortSwigger/extension-portal) with:
   - Link to your GitHub repo
   - Name: **Surface Map**
   - Description: *Passively maps the functional attack surface of a web application as you browse through Burp's proxy. Captures endpoints, groups them by function, tracks shared parameters, and renders a visual map with pan/zoom, copy, and PNG export.*
   - Setup: *Build with `./gradlew build`, load `build/libs/surface-map.jar` as a Java extension. No dependencies beyond Burp itself. Define target scope before capturing.*
4. Track progress on the [Extension submissions board](https://github.com/orgs/PortSwigger/projects/1/views/1).
