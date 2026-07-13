# Tech Stack

**Language**: Kotlin 2.4.0

**Framework**: Jetpack Compose

**Config/Storage**: Jetpack DataStore (Proto)

**Build**:
- Gradle 8.14.5 (wrapper-pinned)
- AGP 8.13.2
- JDK 21
- minSdk: 30 (Shield + Xiaomi Stick run Android 11)
- compileSdk/targetSdk: 36

**Linting & Analysis**:
- ktlint-gradle 14.2.0 (format)
- detekt 1.23.8 (static analysis)

**Testing**:
- JUnit5
- kotlin.test

**Proto Buffers**: Language enum (`Language.FOLLOW_SYSTEM`, `Language.RU`, `Language.EN`, `Language.UNRECOGNIZED`) already generated in `ru.aensidhe.dreamclock.settings` package.
