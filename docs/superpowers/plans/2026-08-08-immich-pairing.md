# Immich QR / Local-Network Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a phone hand Immich credentials to the TV over the LAN by scanning a QR code, with no cloud relay, via a short-lived encrypted local HTTP handshake.

**Architecture:** The TV runs an embedded Ktor CIO server bound to a chosen LAN interface while a full-screen pairing screen is open. It shows a QR whose URL fragment carries a fresh 256-bit AES-GCM key. The phone loads a self-contained page (with a vendored `@noble/ciphers` AES-GCM), encrypts the credential payload with that key, and POSTs it back. The TV decrypts, either validates a pasted key or logs in and mints a least-privilege key, then saves it through the existing Keystore-backed store. Pure logic (crypto, URL, interface selection, payload, mint requests) lives in `:core` or as pure `:app` units with hermetic tests; the server, QR bitmap, Compose screen, and HTML asset are validated on-device.

**Tech Stack:** Kotlin 2.4.0, Jetpack Compose + androidx.tv.material3, Ktor CIO (embedded server), ZXing (QR), kotlinx.serialization, Retrofit/OkHttp, Proto DataStore, `javax.crypto` AES-GCM, vendored `@noble/ciphers`.

## Global Constraints

- `:core` is pure-Kotlin JVM with no Android dependencies; all Android/network/UI code lives in `:app`.
- Kotlin 2.4.0; `minSdk` 30; `compileSdk`/`targetSdk` 36; JDK 21; Gradle 8.14.5 / AGP 8.13.2.
- Canonical gate for every task: `./gradlew verify` (ktlint, detekt, all unit tests, assemble both modules) must be green.
- Tests: TDD for pure-logic units with case tables; pragmatic tests elsewhere; Compose/Ktor/asset paths validated on-device (no adb — sideload the debug APK over LocalSend).
- AES-GCM nonce (IV) is 12 bytes (96 bits), the NIST SP 800-38D recommended size, both sides.
- Minted key permissions are exactly `asset.read`, `asset.view`, `asset.download`.
- The vendored crypto is the AES-GCM part of `@noble/ciphers`, pinned to an exact version, checked in as an app asset with its license header retained; GCM is never hand-rolled.
- Commits: Conventional Commits with a `:robot:` marker after the type (e.g. `feat: :robot: …`); no `Co-Authored-By` trailer.
- Integrate via feature branch → PR → CI green → local `git merge --ff-only` → push; `main` requires signed commits and the `build` status check.
- Markdown in docs uses no bold/italic for inline emphasis.
- All new user-facing strings are localized Ru/En in `res/values/strings.xml` and `res/values-ru/strings.xml`.

---

### Task 1: `PairingCrypto` — AES-GCM in `:core`

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingCrypto.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingCryptoTest.kt`

**Interfaces:**
- Consumes: nothing (JDK `javax.crypto` only).
- Produces:
  - `object PairingCrypto`
  - `fun PairingCrypto.encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray` — returns ciphertext with the 16-byte GCM tag appended.
  - `fun PairingCrypto.decrypt(key: ByteArray, iv: ByteArray, ciphertextAndTag: ByteArray): ByteArray` — returns plaintext; throws `javax.crypto.AEADBadTagException` if the tag fails.

- [ ] **Step 1: Write the failing test**

Uses the NIST SP 800-38D AES-256-GCM known-answer vector (Test Case with 96-bit IV). This same vector is what the phone-side `@noble/ciphers` must reproduce, so it doubles as the cross-implementation guard.

```kotlin
package ru.aensidhe.dreamclock.core.pairing

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun hex(s: String): ByteArray =
    s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

class PairingCryptoTest {
    // NIST AES-256-GCM, 96-bit IV. key/iv/plaintext/aad -> ciphertext||tag.
    private val key = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
    private val iv = hex("cafebabefacedbaddecaf888")
    private val plaintext =
        hex(
            "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39",
        )
    private val expected =
        hex(
            "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662" +
                "76fc6ece0f4e1768cddf8853bb2d551b",
        )

    @Test
    fun encrypt_matches_the_nist_vector() {
        assertEquals(expected.hex(), PairingCrypto.encrypt(key, iv, plaintext).hex())
    }

    @Test
    fun decrypt_round_trips() {
        assertEquals(plaintext.hex(), PairingCrypto.decrypt(key, iv, expected).hex())
    }

    @Test
    fun decrypt_rejects_a_tampered_tag() {
        val tampered = expected.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertFailsWith<AEADBadTagException> { PairingCrypto.decrypt(key, iv, tampered) }
    }

    @Test
    fun a_fresh_random_iv_still_round_trips() {
        val freshIv = ByteArray(12) { it.toByte() }
        val ct = PairingCrypto.encrypt(key, freshIv, "hello".toByteArray())
        assertTrue(ct.size > 5)
        assertEquals("hello", String(PairingCrypto.decrypt(key, freshIv, ct)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.PairingCryptoTest'`
Expected: FAIL — `PairingCrypto` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.pairing

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PairingCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = run(Cipher.ENCRYPT_MODE, key, iv, plaintext)

    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray = run(Cipher.DECRYPT_MODE, key, iv, ciphertextAndTag)

    private fun run(
        mode: Int,
        key: ByteArray,
        iv: ByteArray,
        input: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(input)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.PairingCryptoTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingCrypto.kt \
        core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingCryptoTest.kt
git commit -m "feat: :robot: add AES-GCM PairingCrypto with the NIST cross-impl vector"
```

---

### Task 2: `PairingUrl` — QR URL builder in `:core`

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingUrl.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingUrlTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `fun PairingUrl.build(address: String, port: Int, keyBase64Url: String): String` — returns `http://<host>:<port>/#k=<key>`, bracketing IPv6 literals.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingUrlTest {
    @Test
    fun ipv4_stays_bare() {
        assertEquals(
            "http://192.168.1.42:8973/#k=AAABBB",
            PairingUrl.build("192.168.1.42", 8973, "AAABBB"),
        )
    }

    @Test
    fun ipv6_is_bracketed() {
        assertEquals(
            "http://[fd00::1a2b]:8973/#k=AAABBB",
            PairingUrl.build("fd00::1a2b", 8973, "AAABBB"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.PairingUrlTest'`
Expected: FAIL — `PairingUrl` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.pairing

object PairingUrl {
    fun build(
        address: String,
        port: Int,
        keyBase64Url: String,
    ): String {
        val host = if (address.contains(':')) "[$address]" else address
        return "http://$host:$port/#k=$keyBase64Url"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.PairingUrlTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingUrl.kt \
        core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/PairingUrlTest.kt
git commit -m "feat: :robot: build the pairing QR URL with IPv6 bracketing"
```

---

### Task 3: `InterfaceSelection` — filter, order, resolve in `:core`

**Files:**
- Create: `core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/InterfaceSelection.kt`
- Test: `core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/InterfaceSelectionTest.kt`

**Interfaces:**
- Consumes: nothing (classifies literal address strings via JDK `java.net.InetAddress`, hermetic).
- Produces:
  - `enum class AddressFamily { IPV4, IPV6 }`
  - `data class NicAddress(val interfaceName: String, val address: String, val isUp: Boolean, val isLoopback: Boolean)`
  - `data class PairingAddress(val interfaceName: String, val address: String, val family: AddressFamily)`
  - `fun InterfaceSelection.candidates(records: List<NicAddress>): List<PairingAddress>` — filters `isUp && !isLoopback && !isLinkLocal`, orders v4-site-local → v4-global → v6-ULA → v6-global.
  - `fun InterfaceSelection.resolve(records: List<NicAddress>, interfaceName: String?, family: AddressFamily?): PairingAddress?` — the saved (name+family) match, else the top candidate, else null.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.core.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterfaceSelectionTest {
    private fun up(
        name: String,
        addr: String,
    ) = NicAddress(name, addr, isUp = true, isLoopback = false)

    @Test
    fun drops_loopback_down_and_link_local() {
        val records =
            listOf(
                NicAddress("lo", "127.0.0.1", isUp = true, isLoopback = true),
                NicAddress("eth0", "192.168.1.42", isUp = false, isLoopback = false),
                up("eth1", "169.254.5.5"),
                up("eth2", "fe80::1"),
                up("eth3", "192.168.1.50"),
            )
        assertEquals(
            listOf(PairingAddress("eth3", "192.168.1.50", AddressFamily.IPV4)),
            InterfaceSelection.candidates(records),
        )
    }

    @Test
    fun orders_by_family_and_scope() {
        val records =
            listOf(
                up("a", "2001:db8::1"), // v6 global
                up("b", "8.8.8.8"), // v4 global
                up("c", "fd00::1"), // v6 ULA
                up("d", "10.0.0.5"), // v4 site-local
            )
        assertEquals(
            listOf("10.0.0.5", "8.8.8.8", "fd00::1", "2001:db8::1"),
            InterfaceSelection.candidates(records).map { it.address },
        )
    }

    @Test
    fun resolve_prefers_saved_name_and_family() {
        val records = listOf(up("eth0", "192.168.1.42"), up("wlan0", "10.0.0.9"))
        assertEquals(
            "10.0.0.9",
            InterfaceSelection.resolve(records, "wlan0", AddressFamily.IPV4)?.address,
        )
    }

    @Test
    fun resolve_falls_back_to_top_when_saved_is_gone() {
        val records = listOf(up("eth0", "192.168.1.42"))
        assertEquals(
            "192.168.1.42",
            InterfaceSelection.resolve(records, "wlan0", AddressFamily.IPV4)?.address,
        )
    }

    @Test
    fun resolve_is_null_when_nothing_qualifies() {
        assertNull(InterfaceSelection.resolve(emptyList(), null, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.InterfaceSelectionTest'`
Expected: FAIL — symbols unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package ru.aensidhe.dreamclock.core.pairing

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

enum class AddressFamily { IPV4, IPV6 }

data class NicAddress(
    val interfaceName: String,
    val address: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
)

data class PairingAddress(
    val interfaceName: String,
    val address: String,
    val family: AddressFamily,
)

object InterfaceSelection {
    fun candidates(records: List<NicAddress>): List<PairingAddress> =
        records
            .mapNotNull { record ->
                if (!record.isUp || record.isLoopback) return@mapNotNull null
                val inet = runCatching { InetAddress.getByName(record.address) }.getOrNull()
                    ?: return@mapNotNull null
                if (inet.isLinkLocalAddress) return@mapNotNull null
                val rank = rank(inet) ?: return@mapNotNull null
                val family = if (inet is Inet4Address) AddressFamily.IPV4 else AddressFamily.IPV6
                rank to PairingAddress(record.interfaceName, record.address, family)
            }.sortedBy { it.first }
            .map { it.second }

    fun resolve(
        records: List<NicAddress>,
        interfaceName: String?,
        family: AddressFamily?,
    ): PairingAddress? {
        val ordered = candidates(records)
        val saved =
            ordered.firstOrNull { it.interfaceName == interfaceName && it.family == family }
        return saved ?: ordered.firstOrNull()
    }

    private fun rank(inet: InetAddress): Int? =
        when {
            inet is Inet4Address && inet.isSiteLocalAddress -> 0
            inet is Inet4Address -> 1
            inet is Inet6Address && isUniqueLocal(inet) -> 2
            inet is Inet6Address -> 3
            else -> null
        }

    private fun isUniqueLocal(inet: Inet6Address): Boolean {
        val first = inet.address.first().toInt() and 0xFE
        return first == 0xFC
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests 'ru.aensidhe.dreamclock.core.pairing.InterfaceSelectionTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/ru/aensidhe/dreamclock/core/pairing/InterfaceSelection.kt \
        core/src/test/kotlin/ru/aensidhe/dreamclock/core/pairing/InterfaceSelectionTest.kt
git commit -m "feat: :robot: filter and rank pairing interfaces"
```

---

### Task 4: Proto fields for the persisted interface choice

**Files:**
- Modify: `app/src/main/proto/settings.proto`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/settings/PairingSettingsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: generated `Settings` gains `String getPairingInterfaceName()` and `AddressFamilyProto getPairingAddressFamily()` (`IPV4 = 0`, `IPV6 = 1`), with builder setters `setPairingInterfaceName` / `setPairingAddressFamily`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingSettingsTest {
    @Test
    fun persists_interface_name_and_family() {
        val settings =
            Settings.newBuilder()
                .setPairingInterfaceName("eth0")
                .setPairingAddressFamily(AddressFamilyProto.IPV6)
                .build()
        assertEquals("eth0", settings.pairingInterfaceName)
        assertEquals(AddressFamilyProto.IPV6, settings.pairingAddressFamily)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.settings.PairingSettingsTest'`
Expected: FAIL — `AddressFamilyProto` / setters unresolved.

- [ ] **Step 3: Add the proto fields**

Append to `message Settings` and add the enum (field numbers 15/16 are the next free ones):

```proto
enum AddressFamilyProto { IPV4 = 0; IPV6 = 1; }

message Settings {
  // ... existing fields 1..14 unchanged ...
  string pairing_interface_name = 15;
  AddressFamilyProto pairing_address_family = 16;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.settings.PairingSettingsTest'`
Expected: PASS (proto regenerates on build).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/proto/settings.proto \
        app/src/test/kotlin/ru/aensidhe/dreamclock/settings/PairingSettingsTest.kt
git commit -m "feat: :robot: persist the chosen pairing interface and family"
```

---

### Task 5: Immich login + mint DTOs and endpoints

**Files:**
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichApi.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichMintTest.kt`

**Interfaces:**
- Consumes: `immichJson` (existing), `ImmichClient.api(host, client)` (existing).
- Produces:
  - `@Serializable data class LoginRequest(val email: String, val password: String)`
  - `@Serializable data class LoginResponse(val accessToken: String)`
  - `@Serializable data class CreateApiKeyRequest(val name: String, val permissions: List<String>)`
  - `@Serializable data class CreateApiKeyResponse(val secret: String)`
  - `suspend fun ImmichApi.login(body: LoginRequest): LoginResponse` → `POST api/auth/login`
  - `suspend fun ImmichApi.createApiKey(bearer: String, body: CreateApiKeyRequest): CreateApiKeyResponse` → `POST api/api-keys`, `Authorization` header
  - `val MINT_PERMISSIONS: List<String> = listOf("asset.read", "asset.view", "asset.download")`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.immich

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImmichMintTest {
    private val server = MockWebServer()

    @AfterTest fun tearDown() = server.shutdown()

    @Test
    fun login_posts_credentials_and_reads_the_token() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"accessToken":"tok123"}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val response = api.login(LoginRequest("a@b.c", "pw"))

            val recorded = server.takeRequest()
            assertEquals("/api/auth/login", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"email\":\"a@b.c\""))
            assertEquals("tok123", response.accessToken)
        }

    @Test
    fun create_api_key_sends_bearer_and_read_only_permissions() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"secret":"minted-key"}"""))
            val api = ImmichClient.api(server.url("/").toString())

            val response =
                api.createApiKey("Bearer tok123", CreateApiKeyRequest("Reverie TV", MINT_PERMISSIONS))

            val recorded = server.takeRequest()
            assertEquals("/api/api-keys", recorded.path)
            assertEquals("Bearer tok123", recorded.getHeader("Authorization"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("asset.read"))
            assertTrue(body.contains("asset.view"))
            assertTrue(body.contains("asset.download"))
            assertEquals("minted-key", response.secret)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichMintTest'`
Expected: FAIL — DTOs and endpoints unresolved.

- [ ] **Step 3: Write the implementation**

Add to `ImmichModels.kt`:

```kotlin
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val permissions: List<String>,
)

@Serializable
data class CreateApiKeyResponse(
    val secret: String,
)

val MINT_PERMISSIONS: List<String> = listOf("asset.read", "asset.view", "asset.download")
```

Add to the `ImmichApi` interface:

```kotlin
@POST("api/auth/login")
suspend fun login(
    @Body request: LoginRequest,
): LoginResponse

@POST("api/api-keys")
suspend fun createApiKey(
    @Header("Authorization") authorization: String,
    @Body request: CreateApiKeyRequest,
): CreateApiKeyResponse
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.immich.ImmichMintTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichModels.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/immich/ImmichApi.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/immich/ImmichMintTest.kt
git commit -m "feat: :robot: add Immich login and read-only key minting endpoints"
```

---

### Task 6: `PairingPayload` — decrypted-payload and wire-envelope parsing

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingPayload.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingPayloadTest.kt`

**Interfaces:**
- Consumes: nothing (its own `Json`).
- Produces:
  - `@Serializable data class PairingEnvelope(val iv: String, val ciphertext: String)` (both base64url, no padding)
  - `@Serializable data class PairingPayload(val mode: String, val host: String, val apiKey: String? = null, val email: String? = null, val password: String? = null)`
  - `object PairingCodec { fun parseEnvelope(json: String): PairingEnvelope; fun parsePayload(json: String): PairingPayload; fun decodeBase64Url(value: String): ByteArray }`
  - Mode constants: `PairingPayload.MODE_KEY = "key"`, `PairingPayload.MODE_LOGIN = "login"`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingPayloadTest {
    @Test
    fun parses_the_envelope() {
        val env = PairingCodec.parseEnvelope("""{"iv":"AAAA","ciphertext":"BBBB"}""")
        assertEquals("AAAA", env.iv)
        assertEquals("BBBB", env.ciphertext)
    }

    @Test
    fun parses_a_key_mode_payload_ignoring_unknown_fields() {
        val payload =
            PairingCodec.parsePayload(
                """{"mode":"key","host":"http://immich","apiKey":"k","extra":1}""",
            )
        assertEquals(PairingPayload.MODE_KEY, payload.mode)
        assertEquals("http://immich", payload.host)
        assertEquals("k", payload.apiKey)
    }

    @Test
    fun decodes_url_safe_base64_without_padding() {
        // "hello" -> aGVsbG8
        assertEquals("hello", String(PairingCodec.decodeBase64Url("aGVsbG8")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingPayloadTest'`
Expected: FAIL — symbols unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

@Serializable
data class PairingEnvelope(
    val iv: String,
    val ciphertext: String,
)

@Serializable
data class PairingPayload(
    val mode: String,
    val host: String,
    val apiKey: String? = null,
    val email: String? = null,
    val password: String? = null,
) {
    companion object {
        const val MODE_KEY = "key"
        const val MODE_LOGIN = "login"
    }
}

object PairingCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseEnvelope(text: String): PairingEnvelope = json.decodeFromString(text)

    fun parsePayload(text: String): PairingPayload = json.decodeFromString(text)

    fun decodeBase64Url(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingPayloadTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingPayload.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingPayloadTest.kt
git commit -m "feat: :robot: parse the pairing envelope and credential payload"
```

---

### Task 7: `PairingController` — decrypt, validate/mint, save

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingController.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingControllerTest.kt`

**Interfaces:**
- Consumes: `PairingCrypto` (Task 1), `PairingCodec` / `PairingPayload` / `PairingEnvelope` (Task 6), `ImmichApi.login` / `createApiKey` / `MINT_PERMISSIONS` (Task 5), `ImmichHealth.probe` (existing), `SimilarTimeWindows.windowFor` (existing), `ImmichCredentials` (existing).
- Produces:
  - `sealed interface PairingOutcome { data object Saved : PairingOutcome; data class Failed(val reason: String) : PairingOutcome }`
  - `class PairingController(private val key: ByteArray, private val apiFactory: (String) -> ImmichApi, private val zone: java.time.ZoneId, private val save: suspend (ImmichCredentials) -> Unit)`
  - `suspend fun PairingController.receive(envelopeJson: String, today: java.time.LocalDate, daysEitherSide: Int): PairingOutcome`

- [ ] **Step 1: Write the failing test**

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import ru.aensidhe.dreamclock.core.pairing.PairingCrypto
import ru.aensidhe.dreamclock.immich.ImmichClient
import ru.aensidhe.dreamclock.immich.ImmichCredentials
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingControllerTest {
    private val key = ByteArray(32) { it.toByte() }
    private val iv = ByteArray(12) { (it + 1).toByte() }

    private fun envelope(payloadJson: String): String {
        val ct = PairingCrypto.encrypt(key, iv, payloadJson.toByteArray())
        val enc = Base64.getUrlEncoder().withoutPadding()
        return """{"iv":"${enc.encodeToString(iv)}","ciphertext":"${enc.encodeToString(ct)}"}"""
    }

    @Test
    fun key_mode_validates_then_saves() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("""{"assets":{"total":3,"count":1,"items":[]}}"""))
            var saved: ImmichCredentials? = null
            val host = server.url("/").toString()
            val controller =
                PairingController(
                    key = key,
                    apiFactory = { ImmichClient.api(it) },
                    zone = ZoneId.of("UTC"),
                    save = { saved = it },
                )

            val outcome =
                controller.receive(
                    envelope("""{"mode":"key","host":"$host","apiKey":"secret-key"}"""),
                    today = LocalDate.of(2026, 8, 8),
                    daysEitherSide = 3,
                )

            assertEquals(PairingOutcome.Saved, outcome)
            assertEquals("secret-key", saved?.apiKey)
            server.shutdown()
        }

    @Test
    fun login_mode_mints_then_saves() =
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setBody("""{"accessToken":"tok"}"""))
            server.enqueue(MockResponse().setBody("""{"secret":"minted"}"""))
            var saved: ImmichCredentials? = null
            val host = server.url("/").toString()
            val controller =
                PairingController(key, { ImmichClient.api(it) }, ZoneId.of("UTC")) { saved = it }

            val outcome =
                controller.receive(
                    envelope("""{"mode":"login","host":"$host","email":"a@b.c","password":"pw"}"""),
                    today = LocalDate.of(2026, 8, 8),
                    daysEitherSide = 3,
                )

            assertEquals(PairingOutcome.Saved, outcome)
            assertEquals("minted", saved?.apiKey)
            assertEquals("/api/auth/login", server.takeRequest().path)
            assertEquals("/api/api-keys", server.takeRequest().path)
        }

    @Test
    fun a_tampered_envelope_fails_without_saving() =
        runBlocking {
            var saved: ImmichCredentials? = null
            val controller =
                PairingController(key, { ImmichClient.api(it) }, ZoneId.of("UTC")) { saved = it }
            val good = envelope("""{"mode":"key","host":"http://x","apiKey":"k"}""")
            val tampered = good.dropLast(3) + "AAA\""

            val outcome =
                controller.receive(tampered, LocalDate.of(2026, 8, 8), 3)

            assertTrue(outcome is PairingOutcome.Failed)
            assertEquals(null, saved)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingControllerTest'`
Expected: FAIL — `PairingController` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.aensidhe.dreamclock.pairing

import ru.aensidhe.dreamclock.core.photos.SimilarTimeWindows
import ru.aensidhe.dreamclock.core.pairing.PairingCrypto
import ru.aensidhe.dreamclock.immich.CreateApiKeyRequest
import ru.aensidhe.dreamclock.immich.ImmichApi
import ru.aensidhe.dreamclock.immich.ImmichCredentials
import ru.aensidhe.dreamclock.immich.ImmichHealth
import ru.aensidhe.dreamclock.immich.LoginRequest
import ru.aensidhe.dreamclock.immich.MINT_PERMISSIONS
import ru.aensidhe.dreamclock.immich.ProbeResult
import java.time.LocalDate
import java.time.ZoneId

sealed interface PairingOutcome {
    data object Saved : PairingOutcome

    data class Failed(
        val reason: String,
    ) : PairingOutcome
}

class PairingController(
    private val key: ByteArray,
    private val apiFactory: (String) -> ImmichApi,
    private val zone: ZoneId,
    private val save: suspend (ImmichCredentials) -> Unit,
) {
    @Suppress("ReturnCount")
    suspend fun receive(
        envelopeJson: String,
        today: LocalDate,
        daysEitherSide: Int,
    ): PairingOutcome {
        val payload =
            runCatching {
                val envelope = PairingCodec.parseEnvelope(envelopeJson)
                val iv = PairingCodec.decodeBase64Url(envelope.iv)
                val ciphertext = PairingCodec.decodeBase64Url(envelope.ciphertext)
                PairingCodec.parsePayload(String(PairingCrypto.decrypt(key, iv, ciphertext)))
            }.getOrElse { return PairingOutcome.Failed("decrypt") }

        val api = apiFactory(payload.host)
        return when (payload.mode) {
            PairingPayload.MODE_KEY -> saveValidatedKey(api, payload.host, payload.apiKey, today, daysEitherSide)
            PairingPayload.MODE_LOGIN -> mintAndSave(api, payload.host, payload.email, payload.password)
            else -> PairingOutcome.Failed("mode")
        }
    }

    private suspend fun saveValidatedKey(
        api: ImmichApi,
        host: String,
        apiKey: String?,
        today: LocalDate,
        daysEitherSide: Int,
    ): PairingOutcome {
        if (apiKey.isNullOrBlank()) return PairingOutcome.Failed("missing key")
        val window = SimilarTimeWindows.windowFor(today, daysEitherSide, 0)
        return when (ImmichHealth.probe(api, apiKey, window, zone)) {
            is ProbeResult.Reachable -> {
                save(ImmichCredentials(host, apiKey))
                PairingOutcome.Saved
            }
            else -> PairingOutcome.Failed("validate")
        }
    }

    private suspend fun mintAndSave(
        api: ImmichApi,
        host: String,
        email: String?,
        password: String?,
    ): PairingOutcome {
        if (email.isNullOrBlank() || password.isNullOrBlank()) return PairingOutcome.Failed("missing login")
        return runCatching {
            val token = api.login(LoginRequest(email, password)).accessToken
            val secret =
                api.createApiKey("Bearer $token", CreateApiKeyRequest("Reverie TV", MINT_PERMISSIONS)).secret
            save(ImmichCredentials(host, secret))
            PairingOutcome.Saved
        }.getOrElse { PairingOutcome.Failed("mint") }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingController.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingControllerTest.kt
git commit -m "feat: :robot: orchestrate decrypt, validate or mint, and save on pairing"
```

---

### Task 8: Dependencies and the vendored crypto asset

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/assets/pairing/noble-ciphers.js` (vendored)
- Create: `app/src/main/assets/pairing/NOBLE-LICENSE` (upstream MIT text)

**Interfaces:**
- Consumes: nothing.
- Produces: Ktor CIO server and ZXing on the `:app` classpath; the vendored `@noble/ciphers` ESM bundle and its license available as assets. No Kotlin symbols.

- [ ] **Step 1: Add catalog entries**

In `gradle/libs.versions.toml` under `[versions]`:

```toml
ktor = "3.2.0"
zxing = "3.5.3"
```

Under `[libraries]`:

```toml
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-cio = { module = "io.ktor:ktor-server-cio", version.ref = "ktor" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing" }
```

- [ ] **Step 2: Wire the dependencies**

In `app/build.gradle.kts` `dependencies { }`:

```kotlin
implementation(libs.ktor.server.core)
implementation(libs.ktor.server.cio)
implementation(libs.zxing.core)
```

- [ ] **Step 3: Vendor the crypto bundle**

Download the pinned, self-contained ESM bundle and its license into the assets folder (run once; the file is committed):

```bash
mkdir -p app/src/main/assets/pairing
curl -L "https://cdn.jsdelivr.net/npm/@noble/ciphers@1.2.1/+esm" \
  -o app/src/main/assets/pairing/noble-ciphers.js
curl -L "https://raw.githubusercontent.com/paulmillr/noble-ciphers/1.2.1/LICENSE" \
  -o app/src/main/assets/pairing/NOBLE-LICENSE
```

Verify the file exports `gcm` (it is imported by the page in Task 10):

```bash
grep -c "gcm" app/src/main/assets/pairing/noble-ciphers.js
```

Expected: a non-zero count.

- [ ] **Step 4: Verify the build gate**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, with the new dependencies resolved and the asset packaged.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/assets/pairing/
git commit -m "build: :robot: add Ktor CIO, ZXing, and the vendored @noble/ciphers asset"
```

---

### Task 9: `LanInterfaces` and `PairingQr`

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/LanInterfaces.kt`
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingQr.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingQrTest.kt`

**Interfaces:**
- Consumes: `NicAddress` (Task 3), `PairingUrl` (Task 2), ZXing (Task 8).
- Produces:
  - `object LanInterfaces { fun enumerate(): List<NicAddress> }` — reads real `java.net.NetworkInterface`s into records.
  - `object PairingQr { fun matrix(url: String, size: Int = 512): com.google.zxing.common.BitMatrix; fun bitmap(url: String, size: Int = 512): android.graphics.Bitmap }`

- [ ] **Step 1: Write the failing test**

The ZXing `BitMatrix` is pure JVM and hermetic; the `Bitmap` overload is exercised on-device.

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingQrTest {
    @Test
    fun encodes_a_non_empty_square_matrix() {
        val matrix = PairingQr.matrix("http://192.168.1.42:8973/#k=abc", size = 256)
        assertEquals(matrix.width, matrix.height)
        var dark = 0
        for (x in 0 until matrix.width) {
            if (matrix.get(x, matrix.height / 2)) dark++
        }
        assertTrue(dark > 0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingQrTest'`
Expected: FAIL — `PairingQr` unresolved.

- [ ] **Step 3: Write the implementation**

`PairingQr.kt`:

```kotlin
package ru.aensidhe.dreamclock.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

object PairingQr {
    fun matrix(
        url: String,
        size: Int = 512,
    ): BitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)

    fun bitmap(
        url: String,
        size: Int = 512,
    ): Bitmap {
        val matrix = matrix(url, size)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
```

`LanInterfaces.kt`:

```kotlin
package ru.aensidhe.dreamclock.pairing

import ru.aensidhe.dreamclock.core.pairing.NicAddress
import java.net.NetworkInterface

object LanInterfaces {
    fun enumerate(): List<NicAddress> =
        NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .flatMap { nic ->
                val up = runCatching { nic.isUp }.getOrDefault(false)
                val loopback = runCatching { nic.isLoopback }.getOrDefault(true)
                nic.inetAddresses.toList().map { addr ->
                    NicAddress(
                        interfaceName = nic.name,
                        address = addr.hostAddress?.substringBefore('%').orEmpty(),
                        isUp = up,
                        isLoopback = loopback,
                    )
                }
            }.filter { it.address.isNotBlank() }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingQrTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/LanInterfaces.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingQr.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingQrTest.kt
git commit -m "feat: :robot: enumerate LAN interfaces and render the pairing QR"
```

---

### Task 10: The phone page asset

**Files:**
- Create: `app/src/main/assets/pairing/index.html`

**Interfaces:**
- Consumes: `noble-ciphers.js` (Task 8), served same-origin as `./noble-ciphers.js`.
- Produces: a self-contained page that reads the fragment key, scrubs it from history, offers both modes, AES-GCM-encrypts the payload with a random 12-byte IV, and POSTs `{iv, ciphertext}` (base64url, no padding) to `/pair`. No Kotlin symbols. The page is served by Task 11.

- [ ] **Step 1: Write the page**

The page uses ES modules (allowed over plain HTTP) and `crypto.getRandomValues` (available in non-secure contexts, unlike `crypto.subtle`).

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Reverie pairing</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 0; padding: 24px; }
  input, button { font-size: 18px; padding: 12px; width: 100%; box-sizing: border-box; margin: 6px 0; }
  .tab { display: none; } .tab.active { display: block; }
  nav button { width: 48%; display: inline-block; }
  #status { margin-top: 16px; font-weight: 600; }
</style>
</head>
<body>
<h1>Pair with your TV</h1>
<nav>
  <button id="tabKey">Paste API key</button>
  <button id="tabLogin">Email &amp; password</button>
</nav>
<section id="key" class="tab active">
  <input id="keyHost" placeholder="Immich URL (https://immich.example)" />
  <input id="keyValue" placeholder="API key" />
  <button data-mode="key">Send key</button>
</section>
<section id="login" class="tab">
  <input id="loginHost" placeholder="Immich URL (https://immich.example)" />
  <input id="loginEmail" placeholder="Email" />
  <input id="loginPassword" type="password" placeholder="Password" />
  <button data-mode="login">Log in and pair</button>
</section>
<div id="status"></div>
<script type="module">
import { gcm } from './noble-ciphers.js';

const rawKey = new URLSearchParams(location.hash.slice(1)).get('k');
history.replaceState(null, '', location.pathname);

function b64urlToBytes(s) {
  const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
  const b = atob(s.replace(/-/g, '+').replace(/_/g, '/') + pad);
  return Uint8Array.from(b, c => c.charCodeAt(0));
}
function bytesToB64url(bytes) {
  let s = btoa(String.fromCharCode(...bytes));
  return s.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

const key = b64urlToBytes(rawKey);
const status = document.getElementById('status');

function show(id) {
  for (const el of document.querySelectorAll('.tab')) el.classList.remove('active');
  document.getElementById(id).classList.add('active');
}
document.getElementById('tabKey').onclick = () => show('key');
document.getElementById('tabLogin').onclick = () => show('login');

function payloadFor(mode) {
  if (mode === 'key') {
    return { mode, host: keyHost.value.trim(), apiKey: keyValue.value.trim() };
  }
  return {
    mode, host: loginHost.value.trim(),
    email: loginEmail.value.trim(), password: loginPassword.value,
  };
}

async function send(mode) {
  status.textContent = 'Sending…';
  try {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plaintext = new TextEncoder().encode(JSON.stringify(payloadFor(mode)));
    const ciphertext = gcm(key, iv).encrypt(plaintext);
    const res = await fetch('/pair', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ iv: bytesToB64url(iv), ciphertext: bytesToB64url(ciphertext) }),
    });
    status.textContent = res.ok ? 'Paired! You can close this page.' : 'Pairing failed. Check the details on the TV.';
  } catch (e) {
    status.textContent = 'Error: ' + e.message;
  }
}
for (const b of document.querySelectorAll('button[data-mode]')) {
  b.onclick = () => send(b.dataset.mode);
}
</script>
</body>
</html>
```

- [ ] **Step 2: Sanity-check the asset is well-formed**

Run: `grep -c "gcm(key, iv).encrypt" app/src/main/assets/pairing/index.html`
Expected: `1` (the encrypt call is present; full behavior is validated on-device in Task 13).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/pairing/index.html
git commit -m "feat: :robot: add the phone pairing page with bundled AES-GCM"
```

---

### Task 11: `PairingServer` — embedded Ktor CIO

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingServer.kt`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingServerAssetsTest.kt`

**Interfaces:**
- Consumes: Ktor CIO (Task 8), `PairingController` (Task 7), the assets from Tasks 8 and 10.
- Produces:
  - `class PairingServer(private val address: String, private val assets: (String) -> ByteArray?, private val onEnvelope: suspend (String) -> Boolean)`
  - `fun PairingServer.start(): Int` — binds to `address` on an ephemeral port, returns the chosen port.
  - `fun PairingServer.stop()`
  - `object PairingAssets { fun read(context: android.content.Context, name: String): ByteArray? }` — reads `assets/pairing/<name>`.

- [ ] **Step 1: Write the failing test**

`PairingAssets` name-mapping is the hermetic seam (the real asset read needs a `Context`, exercised on-device). The test pins the content-type mapping helper.

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingServerAssetsTest {
    @Test
    fun maps_asset_names_to_content_types() {
        assertEquals("text/html; charset=utf-8", PairingServer.contentTypeFor("index.html"))
        assertEquals("text/javascript; charset=utf-8", PairingServer.contentTypeFor("noble-ciphers.js"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingServerAssetsTest'`
Expected: FAIL — `PairingServer` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package ru.aensidhe.dreamclock.pairing

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode

object PairingAssets {
    fun read(
        context: Context,
        name: String,
    ): ByteArray? = runCatching { context.assets.open("pairing/$name").use { it.readBytes() } }.getOrNull()
}

class PairingServer(
    private val address: String,
    private val assets: (String) -> ByteArray?,
    private val onEnvelope: suspend (String) -> Boolean,
) {
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    fun start(): Int {
        val server =
            embeddedServer(CIO, host = address, port = 0) {
                routing {
                    get("/") { serveAsset("index.html") }
                    get("/{name}") { serveAsset(call.parameters["name"].orEmpty()) }
                    post("/pair") {
                        val ok = onEnvelope(call.receiveText())
                        call.respondText(
                            if (ok) "ok" else "no",
                            status = if (ok) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                        )
                    }
                }
            }
        server.start(wait = false)
        engine = server
        return server.engineConfig.connectors.first().port
    }

    fun stop() {
        engine?.stop(0, 0)
        engine = null
    }

    private suspend fun io.ktor.server.application.ApplicationCall.serveAssetImpl(name: String) {
        val bytes = assets(name)
        if (bytes == null) {
            respondText("not found", status = HttpStatusCode.NotFound)
        } else {
            respondBytes(bytes, ContentType.parse(contentTypeFor(name)))
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.serveAsset(name: String) =
        call.serveAssetImpl(name)

    companion object {
        fun contentTypeFor(name: String): String =
            when {
                name.endsWith(".html") -> "text/html; charset=utf-8"
                name.endsWith(".js") -> "text/javascript; charset=utf-8"
                else -> "application/octet-stream"
            }
    }
}
```

Note for the implementer: the exact Ktor CIO port-readback and `EmbeddedServer` generic types must compile against Ktor 3.2.0; if the `engineConfig.connectors` accessor differs in that version, bind an explicit ephemeral port chosen with `java.net.ServerSocket(0).use { it.localPort }` before `embeddedServer`, and pass it to both `port =` and the return value. Keep the `contentTypeFor` companion (it is what the test pins).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingServerAssetsTest'`
Expected: PASS.

- [ ] **Step 5: Verify the build gate**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingServer.kt \
        app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingServerAssetsTest.kt
git commit -m "feat: :robot: serve the pairing page and accept the encrypted POST"
```

---

### Task 12: Pairing screen, interface selector, and wiring

**Files:**
- Create: `app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingScreen.kt`
- Modify: `app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt` (add the selector row and the "Pair with phone" action inside `ImmichSection`)
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingCountdownTest.kt`

**Interfaces:**
- Consumes: `LanInterfaces` (Task 9), `InterfaceSelection` (Task 3), `PairingUrl` (Task 2), `PairingQr` (Task 9), `PairingServer` (Task 11), `PairingController` (Task 7), `PairingAssets` (Task 11), `PairingCrypto` (Task 1), `KeystoreCipher` / `SettingsRepository` (existing).
- Produces:
  - `object PairingCountdown { fun format(remainingSeconds: Int): String }` — `"4:32"` style.
  - `@Composable fun PairingScreen(address: String, port: Int, keyBase64Url: String, remainingSeconds: Int, status: String, onCancel: () -> Unit)`.
  - An interface-selector row and a "Pair with phone" button in `ImmichSection` that opens the pairing flow.

- [ ] **Step 1: Write the failing test (pure countdown formatting)**

```kotlin
package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals

class PairingCountdownTest {
    @Test
    fun formats_minutes_and_zero_padded_seconds() {
        assertEquals("4:32", PairingCountdown.format(272))
        assertEquals("0:05", PairingCountdown.format(5))
        assertEquals("0:00", PairingCountdown.format(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingCountdownTest'`
Expected: FAIL — `PairingCountdown` unresolved.

- [ ] **Step 3: Write the countdown and the screen**

`PairingCountdown` and `PairingScreen` in `PairingScreen.kt`:

```kotlin
package ru.aensidhe.dreamclock.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

object PairingCountdown {
    fun format(remainingSeconds: Int): String {
        val safe = remainingSeconds.coerceAtLeast(0)
        return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }
}

@Composable
fun PairingScreen(
    address: String,
    port: Int,
    keyBase64Url: String,
    remainingSeconds: Int,
    status: String,
    onCancel: () -> Unit,
) {
    val url = PairingUrl.build(address, port, keyBase64Url)
    val qr = remember(url) { PairingQr.bitmap(url).asImageBitmap() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(qr, contentDescription = null, modifier = Modifier.size(360.dp))
            Text("Scan with your phone's camera")
            Text("Expires in ${PairingCountdown.format(remainingSeconds)}")
            Text(status)
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}
```

- [ ] **Step 4: Add the strings**

In `app/src/main/res/values/strings.xml`:

```xml
<string name="settings_pairing_interface">Pairing interface</string>
<string name="settings_pair_action">Pair with phone</string>
<string name="pairing_scan_hint">Scan with your phone\'s camera</string>
<string name="pairing_expires_in">Expires in %1$s</string>
<string name="pairing_cancel">Cancel</string>
<string name="pairing_waiting">Waiting for your phone…</string>
<string name="pairing_saved">Paired.</string>
<string name="pairing_failed">Pairing failed.</string>
<string name="pairing_no_interface">No usable network interface.</string>
```

In `app/src/main/res/values-ru/strings.xml`:

```xml
<string name="settings_pairing_interface">Сетевой интерфейс</string>
<string name="settings_pair_action">Связать с телефоном</string>
<string name="pairing_scan_hint">Отсканируйте камерой телефона</string>
<string name="pairing_expires_in">Истекает через %1$s</string>
<string name="pairing_cancel">Отмена</string>
<string name="pairing_waiting">Ожидание телефона…</string>
<string name="pairing_saved">Связано.</string>
<string name="pairing_failed">Не удалось связать.</string>
<string name="pairing_no_interface">Нет подходящего сетевого интерфейса.</string>
```

Replace the three literal strings in `PairingScreen` (`"Scan with your phone's camera"`, the `"Expires in …"` text, `"Cancel"`) with `stringResource(...)` calls against these ids; pass `status` in already-localized from the caller.

- [ ] **Step 5: Add the selector and action to `ImmichSection`**

In `SettingsScreen.kt`, inside `ImmichSection` (after the connection test block, before the steppers), add a selector row over `InterfaceSelection.candidates(LanInterfaces.enumerate())` that persists `pairingInterfaceName` + `pairingAddressFamily` via `repository.update`, and a `Button` labelled `R.string.settings_pair_action` that launches the pairing flow. The flow, hosted in the settings composable state:

1. `val chosen = InterfaceSelection.resolve(LanInterfaces.enumerate(), settings.pairingInterfaceName, family(settings.pairingAddressFamily))` — if null, show `R.string.pairing_no_interface` and stop.
2. Generate `val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }` and its `Base64.getUrlEncoder().withoutPadding()` string.
3. Build a `PairingController(key, { ImmichClient.api(it) }, ZoneId.systemDefault()) { creds -> repository.update { it.toBuilder().setImmichHost(creds.host).setImmichKeyCiphertext(ByteString.copyFrom(cipher.encrypt(creds.apiKey))).build() } }`.
4. `val server = PairingServer(chosen.address, { name -> PairingAssets.read(context, name) }) { envelope -> controller.receive(envelope, LocalDate.now(), settings.daysEitherSide) == PairingOutcome.Saved }` and `val port = server.start()`.
5. Show `PairingScreen(chosen.address, port, keyString, remaining, statusText, onCancel = { server.stop() })` with a `LaunchedEffect` that ticks `remaining` down from the window length (300s) once per second and calls `server.stop()` at zero; on a `Saved` outcome, set status to `R.string.pairing_saved` and stop.

Keep the pairing UI state local to the composable (a `var pairing by remember { mutableStateOf(false) }` gate rendering `PairingScreen` full-bleed over the settings content). Reuse the existing `cipher` parameter already threaded into `ImmichSection`.

- [ ] **Step 6: Run the countdown test and the gate**

Run: `./gradlew :app:testDebugUnitTest --tests 'ru.aensidhe.dreamclock.pairing.PairingCountdownTest'`
Expected: PASS.

Run: `./gradlew verify`
Expected: BUILD SUCCESSFUL (ktlint, detekt, tests, assemble both modules).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/ru/aensidhe/dreamclock/pairing/PairingScreen.kt \
        app/src/main/kotlin/ru/aensidhe/dreamclock/settings/SettingsScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml \
        app/src/test/kotlin/ru/aensidhe/dreamclock/pairing/PairingCountdownTest.kt
git commit -m "feat: :robot: add the pairing screen, interface selector, and countdown"
```

---

### Task 13: On-device validation

**Files:**
- None (manual validation; no adb — sideload `app/build/outputs/apk/debug/app-debug.apk` over LocalSend).

**Interfaces:**
- Consumes: the whole feature.
- Produces: a signed-off validation pass.

- [ ] **Step 1: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; carry the APK over with LocalSend and install by hand.

- [ ] **Step 2: Work the checklist on-device**

```
- [ ] Settings > Immich: the interface selector lists real interfaces as "name — address", private LAN addresses on top, and persists across a settings reopen.
- [ ] "Pair with phone" shows a QR, the scan hint, and a live countdown that ticks down.
- [ ] A phone scanning the QR loads the page over plain http; the URL bar shows no #k= after load.
- [ ] Paste-key mode: entering host + a valid key pairs; the TV status shows saved, and the photo deck starts.
- [ ] Email/password mode: entering host + credentials mints a key; the saved key works and the deck starts.
- [ ] A wrong key or bad login shows a failure on the TV and the server keeps listening for a retry.
- [ ] Cancel and letting the countdown reach zero both stop the server (a re-scan then fails to connect).
- [ ] Interface sanity on both an Ethernet Shield and a WiFi stick.
```

- [ ] **Step 3: Commit the validated status**

Update the "Current status" note in `CLAUDE.md` and `README.md` (feature-2 tail) to record pairing as built and on-device-validated, then commit:

```bash
git add CLAUDE.md README.md
git commit -m "docs: :robot: record QR pairing as built and validated on-device"
```

---

## Self-Review

Spec coverage — every section of `2026-08-08-immich-pairing-design.md` maps to a task:

- Ktor CIO server, `GET /` + `POST /pair`, ephemeral port, bound to the selected address → Tasks 8, 11, 12.
- QR with fragment key, one-line hint → Tasks 2, 9, 12.
- History scrub, two modes, vendored `@noble/ciphers`, 12-byte IV, `{iv, ciphertext}` base64url → Tasks 8, 10, 6.
- Decrypt → validate paste-key / login→mint (`asset.read/view/download`, discard password) → Tasks 5, 7.
- Save through the Keystore store → Task 12 (uses existing `KeystoreCipher` + `CredentialsStore`).
- Interface filter (`isUp && !isLoopback && !isLinkLocal`), ordering, per-address rows, persist by name+family, resolve-at-pairing fallback → Tasks 3, 4, 9, 12.
- Countdown, Cancel, single-use ephemeral key → Tasks 12, 7.
- Testing split (hermetic `:core`/`:app`, cross-impl vector via NIST KAT, on-device manual) → Tasks 1, 3, 5, 6, 7, 9, 13.

Placeholder scan — no `TBD`/`TODO`; every code step carries real code. The two prose-heavy steps (Task 11's Ktor note, Task 12 step 5 wiring) name exact types and calls rather than deferring.

Type consistency — `PairingCrypto.encrypt/decrypt(key, iv, …)`, `PairingCodec.parseEnvelope/parsePayload/decodeBase64Url`, `PairingController.receive(envelopeJson, today, daysEitherSide)`, `InterfaceSelection.candidates/resolve`, `PairingServer.start(): Int / stop()`, and `PairingQr.matrix/bitmap` are used with the same signatures across their producing and consuming tasks.

Known risk carried from the spec: Ktor CIO's exact `EmbeddedServer` port-readback API on 3.2.0 (Task 11 note gives the `ServerSocket(0)` fallback), and Immich's acceptance of a `Bearer` session token on `POST /api/api-keys` (validated in Task 13's mint step).
