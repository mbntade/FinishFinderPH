# Finish Finder PH

Mark the finishes in a room photo (floor, walls, cabinets, fixtures…) and search Philippine stores such as Wilcon Depot, IKEA Philippines and Tile Center for similar products.

Your projects and photos are saved **only on the device you use**. Use **Settings › Back up all projects** to keep a copy or move to another device.

## Use it

| Device | How |
|---|---|
| Laptop | Open the website link (below) in Chrome or Edge. To install: click the install icon in the address bar. |
| iPad / iPhone | Open the website link in **Safari** › Share button › **Add to Home Screen**. |
| Android phone | Install the APK (below), or open the website in Chrome › ⋮ › **Add to Home screen**. |

**Website:** `https://mbntade.github.io/FinishFinderPH/` (works once GitHub Pages is switched on)

After the first visit, the website also works offline.

## Get the Android APK

1. Open the **Actions** tab of this repo › **Build Android app** › the latest run with a green tick.
2. Under **Artifacts**, download `FinishFinderPH-apk-…` (a .zip) and unzip it on your phone.
3. Tap the `.apk` file. Android will ask you to allow installs from that app (e.g. Files or Chrome). Allow it, then install.

This is a test build for your own phone, not a Play Store release.

## What's in this repo

| Folder / file | What it is |
|---|---|
| `web/` | The app itself (`index.html`), install/offline files (`manifest.webmanifest`, `sw.js`), icons and fonts. |
| `android/` | Android shell that shows the same `web/` app and adds saving, sharing and the camera. |
| `.github/workflows/pages.yml` | Publishes `web/` to GitHub Pages whenever it changes. |
| `.github/workflows/android.yml` | Builds the test APK whenever `web/` or `android/` changes. |

When you change `web/index.html`, also bump `VERSION` in `web/sw.js` (e.g. `ffph-v2`) so installed copies pick up the update.

## Notes

- Store search results are similar looks, not exact products. Confirm specs, sizes, prices and stock with the store.
- Font: Jost, used under the SIL Open Font License (`web/OFL.txt`).
