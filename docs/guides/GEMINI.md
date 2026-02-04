# LiSynchronization & LX Music Ecosystem

## Project Overview

This workspace contains a multi-faceted project ecosystem focused on **LX Music (LuoXue Music)**, specifically targeting integration with **Xiaomi Wearable devices** and mobile platforms.

The workspace consists of two primary components:
1.  **LiSynchronization (Root Project):** A native Android application acting as a bridge/middleware for Xiaomi watches.
2.  **lx-music-mobile:** A React Native mobile application for LX Music.
3.  **Reference Server (`server_able_run.js`):** A Node.js implementation of the music source proxy logic.

---

## 1. LiSynchronization (Android Project)

**Location:** Root directory (`app/`)
**Type:** Native Android App (Java)

### Purpose
To transplant the audio source relay capability of LX Music to the Android platform. It allows Xiaomi Watch devices to fetch music playback links from various platforms by communicating with this Android App via the Xiaomi Wearable SDK.

### Architecture
*   **Communication:** Uses Xiaomi Wearable SDK (`MessageApi`, `NodeApi`) to listen for requests from the watch.
*   **Core Logic:** `RequestProxy` handles routing requests (Round-Robin or Targeted).
*   **Execution Engine:** Embeds **QuickJS** to run user-imported JavaScript sources (compatible with LX Music Desktop).
*   **Network:** Uses `OkHttp` for direct network requests (no proxy by default, unlike the desktop version which might use one).
*   **Caching:** Implements a 4-hour memory + disk cache strategy.

### Build & Run
*   **Build System:** Gradle
*   **Command:** `./gradlew :app:assembleDebug`
*   **Key Files:**
*   `docs/plans/plan.md`: Detailed architectural blueprint and implementation plan.
    *   `app/src/main/java`: Source code (to be implemented/verified).
    *   `app/build.gradle`: Dependencies (QuickJS, Gson, OkHttp).

---

## 2. lx-music-mobile

**Location:** `lx-music-mobile/`
**Type:** React Native Application

### Purpose
The mobile client version of the LX Music player.

### Tech Stack
*   **Framework:** React Native
*   **State Management:** Redux
*   **Platforms:** Android (focus), iOS (code exists but project emphasizes Android).

### Build & Run
*   **Dependencies:** `npm install` or `yarn` (inside `lx-music-mobile/`)
*   **Run Android:** `npm run dev` or `npx react-native run-android`
*   **Build Release:** `cd android && ./gradlew assembleRelease`

---

## 3. Reference Server (server_able_run.js)

**Location:** `server_able_run.js`
**Type:** Node.js Script

### Purpose
This is a standalone Node.js implementation of the **Audio Source Proxy**. It serves as the logical reference for what `LiSynchronization` aims to achieve on Android.

*   **Framework:** Express.js
*   **Features:**
    *   Loads scripts from a `scripts/` directory.
    *   Uses `vm` to sandbox script execution.
    *   Implements the `lx` object API (request, crypto, utils) expected by music source scripts.
    *   Handling `musicUrl` resolution with caching and load balancing.

---

## Development Context

*   **Goal:** The current primary task (based on `docs/plans/plan.md`) is likely implementing or refining the `LiSynchronization` Android app to match the capabilities of the Node.js reference server (`server_able_run.js`), specifically focusing on the QuickJS integration and Xiaomi Wearable communication.
*   **Conventions:**
    *   **Android:** Java 1.8, standard Android project structure.
    *   **Logic:** The Android implementation attempts to be "API compatible" with the JS scripts used by the Desktop/Node version.
