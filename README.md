# LiSync (LiSynchronization)

LiSync is an Android-native bridge service for LX Music source scripts, designed to work with Xiaomi Wear / Xiaomi Health.  
The watch-side Vela QuickApp sends requests to the phone, which resolves music URLs, search, and lyrics.

## Features
- Run LX music source scripts via QuickJS
- Xiaomi Wearable integration (MessageApi, permissions, capability push)
- Script management (import from file/URL, edit, rename, delete)
- Cache for music URLs (4 hours) and search/lyrics (5 minutes)
- Local music upload to watch (chunked)

## Build
```powershell
cd F:\Project\LiSynchronization
gradlew.bat assembleDebug
```

## Docs
- `docs/LiSync_Protocol_Spec.md`
- `docs/vela_quickapp_integration.md`
- `docs/custom_source.md`

## License
This project is open-sourced under the **GNU AGPL-3.0** license.

### Commercial Use
Commercial use is **not permitted** without explicit permission from the author.  
If you need a commercial license, please contact the maintainer.
