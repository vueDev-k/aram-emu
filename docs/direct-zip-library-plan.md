# Direct ZIP library integration

The Android library already scans a persisted SAF tree recursively and lists ZIP files. The emulator's Android host still copies each selected URI into private imports before calling `Mobile.openDocument(path, title)`.

## Required change

1. Keep a persistable read grant for the selected tree and identify library-origin document intents.
2. Open the selected URI using `ContentResolver.openFileDescriptor(uri, "r")` and check seekability. Do not assume that all document providers expose seekable files.
3. Add a Go/mobile integration interface accepting an owned seekable handle or stream, rather than assuming a content URI is a filesystem path. Preserve display name and extension separately.
4. Keep the descriptor alive for the entire emulator session and release it on game close/replacement. Ensure concurrent game changes and Activity recreation cannot close a descriptor prematurely.
5. Use the ordinary inspection, hashing, profile and open pipeline. Do not bypass compatibility or save-data identity logic.
6. For non-seekable providers, report that direct opening is unsupported instead of silently copying when the user requests no-copy mode.
7. Test ZIP launch, switching games, save/load, app pause/resume, process recreation, permission revocation, and multiple SAF providers on a device.

## Status

This document records the implementation contract; direct execution has **not** been implemented or device-verified. The existing library scan patch must be tested separately.
