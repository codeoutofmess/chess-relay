# Chess Relay

A small Android app that reads the board in the Chess.com app and sends each new position to my portfolio site, which shows the game live on its landing page.

It uses an accessibility service. Chess.com labels every square for screen readers ("White Rook on h1", "g1, empty"), so the app reads the exact position and, from which way round the board is, which colour I'm playing. It only reads while a game clock is on screen, and only inside Chess.com.

Setup on the phone is in `PHONE_SETUP.md` in the portfolio site's repo.

Build (needs Java 17+ and the Android SDK):

```bash
./gradlew testDebugUnitTest assembleDebug
```

The app file lands in `app/build/outputs/apk/debug/app-debug.apk`.
