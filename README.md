# Switch Reborn

An Android home-screen launcher modelled on the Nintendo Switch HOME menu: the software row
with its pulsing selection frame, the round buttons, the status row and hint bar, All Software,
and light/dark themes that follow the system.

Clean-room: no Nintendo code, firmware, artwork or sounds are included or were decrypted. The
layout, colours and timings were measured from public HDMI captures (see `docs/RE_NOTES.md`);
icons are original vector drawings and the UI sounds are synthesized at runtime.

Build: `./gradlew assembleDebug` (minSdk 33). Set as default via Settings > Apps > Default apps >
Home app, or Sleep Mode > Choose HOME App from inside the launcher.

Not affiliated with or endorsed by Nintendo. "Nintendo Switch" is a trademark of Nintendo.
