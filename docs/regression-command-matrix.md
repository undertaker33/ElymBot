# Regression Command Matrix

Phase 6 baseline verification commands:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat compileDebugAndroidTestKotlin`
- `./gradlew.bat assembleDebugAndroidTest`
- `./gradlew.bat assembleDebug`
- `./gradlew.bat assembleRelease`

When a connected device or emulator is available, also run:

- `./gradlew.bat connectedDebugAndroidTest`

Suggested focused commands during iteration:

- `./gradlew.bat testDebugUnitTest --tests com.astrbot.android.ui.viewmodel.*`
- `./gradlew.bat testDebugUnitTest --tests com.astrbot.android.data.http.*`
- `./gradlew.bat testDebugUnitTest --tests com.astrbot.android.runtime.*`
