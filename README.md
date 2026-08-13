# Merchant Payout

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.11-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/minSdk-34-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Hilt](https://img.shields.io/badge/Hilt-2.53-orange)](https://dagger.dev/hilt/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-shaped Android reference implementation of a merchant balance & payout
flow — the class of feature you'd find inside a real fintech app.

Built to demonstrate how I think about correctness, layering, security, and
testability when the stakes are money on the wire.

**Kotlin · Jetpack Compose · Hilt · Retrofit + kotlinx.serialization · Paging 3 ·
AndroidX Biometric · MockWebServer**

---

## What it does

A merchant can:

- Review their available and pending balance with a live activity feed
- Page through their full transaction history (cursor pagination, date-grouped headers)
- Compose a payout — validated IBAN, locale-aware amount input, currency picker
- Confirm the payout on a summary screen, sign with biometrics if the amount is
  material, and watch the status transition live
- See their balance drop and a new activity row appear as soon as the payout settles

No backend to set up. A `MockWebServer` is bundled inside the app and boots inside
the `Application` class, so a debug build gives a fully working end-to-end demo
on any API 34+ device.

---

## Architecture · one page

```
┌─────────────────────────────────────────────────────────┐
│  Compose UI      HomeScreen · TransactionsList · Payout │
│                  sealed UiState { Loading Content Error │
│                  }                                      │
└──────────────────┬───────────────────▲──────────────────┘
   callbacks down  │                   │  state up
                   ▼                   │
┌─────────────────────────────────────────────────────────┐
│  ViewModel       StateFlow<UiState>                     │
│                  no Android UI code · pure Kotlin       │
└──────────────────┬───────────────────▲──────────────────┘
                   ▼                   │
┌─────────────────────────────────────────────────────────┐
│  Repository      suspend fn → Outcome<T>                │
│                  runApi { } wraps Http/IOException →    │
│                  DomainError                            │
└──────────────────┬───────────────────▲──────────────────┘
                   ▼                   │
┌─────────────────────────────────────────────────────────┐
│  Retrofit + OkHttp     @GET @POST @Path @Query          │
│                        kotlinx.serialization (snake)    │
└──────────────────┬───────────────────▲──────────────────┘
                   ▼                   │
┌─────────────────────────────────────────────────────────┐
│  MockServer      bundled · random localhost port        │
│                  MockDispatcher routes · MockData seeds │
└─────────────────────────────────────────────────────────┘
```

Events flow **down** as callbacks. State flows **up** as `StateFlow`.
Every screen exposes a single sealed UI state so composables render
exhaustively with `when`. Repositories return `Outcome<T>` — ViewModels
never see raw exceptions.

---

## Engineering decisions worth reading the code for

### Money never touches a float

Every amount is a `Long` in minor units — pence for GBP, cents for EUR — from
the API wire all the way through to the composable. The parser is a
locale-aware `BigDecimal` conversion that rejects anything with more than 2
decimals, refuses trailing dots mid-typing, and hard-caps at `Int.MAX_VALUE`:

```kotlin
data class Money(val minorUnits: Long, val currency: Currency)
// £999.99 → minorUnits = 99_999
```

The choice removes the entire family of floating-point drift bugs that plague
naive money apps.

### IBAN validation is real

`IbanValidator.mod97()` runs the ISO 7064 mod-97-10 checksum on-device.
Country-length aware, catches ~99.98% of common typos before the request ever
reaches the server:

```kotlin
val rearranged = iban.substring(4) + iban.substring(0, 4)
// walk char by char, A=10..Z=35, keep remainder small
// remainder == 1 → valid
```

Rearranging the string and walking char-by-char keeps the running number
within `Int` range — no `BigInteger` required, no allocation per keystroke.

### Cursor pagination, not page-number

`ActivityPagingSource` uses server-issued cursors. If a new activity row
appears mid-scroll, page numbers would drift and users would see duplicates
or gaps. Cursors point to a specific row and stay coherent even when the
feed changes underneath.

```kotlin
Pager(
  config = PagingConfig(pageSize = 15, prefetchDistance = 5,
                        enablePlaceholders = false),
  pagingSourceFactory = { ActivityPagingSource(api) }
).flow
```

### State machines instead of callback soup

The payout confirm screen has to decide, on tap: proceed silently, prompt
biometrics, ask the user to enrol biometrics, or show a message. Rather than
firing four different callbacks, `handleSendTap` returns a `SendDecision`
sealed class. The composable matches exhaustively:

```kotlin
sealed class SendDecision {
    object Proceed : SendDecision()
    object Silent : SendDecision()
    object NeedsEnrolment : SendDecision()
    data class ShowMessage(val text: String) : SendDecision()
}
```

One return type, exhaustive `when`, testable in isolation.

### Cross-screen refresh without an event bus

`PayoutRefresher` is a `MutableStateFlow<Int>` counter, injected as a
singleton. Successful payouts call `bump()`. Home and Transactions
ViewModels observe `refreshTrigger` and refetch. StateFlow is replayable,
so late subscribers still see the current value. No lost events.

```kotlin
@Singleton
class PayoutRefresher @Inject constructor() {
    private val counter = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = counter.asStateFlow()
    fun bump() { counter.value += 1 }
}
```

---

## Security posture · engineered toward bank-grade

Designed against the same threat model a **PCI DSS Level 1** fintech client
faces: hostile networks, shoulder-surfing, device compromise, replay attacks,
and screen-capture leakage. The choices below are the on-device foundations
that a full compliance program builds on.

### In this codebase

- **Biometric step-up** on every material payout (≥ £1,000). Enforced with
  `BIOMETRIC_STRONG` (Class 3) — the only class Android trusts for
  cryptographic key material. The same prompt can bind a `CryptoObject` for
  signed request payloads without changing the flow.
- **`FLAG_SECURE`** wrapped as a declarative `ScreenSecurityEffect()`
  composable — screenshots return black, Recents preview is blanked, screen
  mirroring to untrusted displays is blocked. Scoped to the payout screens
  only, not the whole app.
- **API 34 `registerScreenCaptureCallback`** with `DETECT_SCREEN_CAPTURE`
  permission → fires an in-app banner the moment a capture is attempted, so
  the merchant is informed rather than silently protected.
- **Device identity** — payout request carries a server-issued `device_id`,
  fetched lazily and cached in an `AtomicReference`. Foundation for later
  device attestation.
- **Domain-typed errors** — server response bodies are parsed with a
  regex-scoped extractor; sensitive backend fields never leak into user-facing
  strings. `DomainError` sealed class controls exactly what surfaces.
- **No sensitive data on disk** — no shared preferences, no unencrypted
  cache, no persistent logging. Everything material lives in memory only.
- **No third-party analytics or crash SDKs** — zero opaque data egress
  paths. Every network call is visible in `MerchantApi`.

### Bank-grade next steps · the compliance roadmap

The following are the deliberate next steps to move from "sound on-device
foundation" to "shippable under a PCI DSS Level 1 program":

- **Certificate pinning** on `OkHttpClient` — pin the fintech's issuing CA and
  fail-closed on any deviation. Blocks MITM even with a compromised device
  trust store.
- **Play Integrity API** attestation — every payout request signed with a
  device attestation token; server rejects unattested clients. Kills root/
  emulator abuse.
- **Rooted / jailbroken detection** — RootBeer or equivalent, run at app
  start; downgrade the UX (read-only) instead of hard-block.
- **Hardware-backed keys via `KeyStore` + StrongBox** — bind a per-device
  signing key to `BIOMETRIC_STRONG` so payout confirmation *actually signs*
  the request payload, not just triggers a UI acknowledgement.
- **Idempotency-Key header** on every payout POST, generated as a UUID
  bound to the biometric session. Server-side dedup prevents double-charge
  on retries.
- **Request signing (HMAC or JWS)** on every mutating call, keyed off the
  hardware-backed private key. Server verifies signature + timestamp;
  replays and tampered payloads are dropped at the edge.
- **TLS 1.3 only**, forward-secret cipher suites only, HSTS honoured.
- **Session binding** — payout submission includes the biometric
  authentication result nonce so a captured request can't be reused after
  the prompt closes.
- **App-shielding** (obfuscation + runtime integrity checks) via R8 in
  aggressive mode plus a commercial shield (Guardsquare / Promon) for the
  release build.
- **Anti-debugger / anti-hook** on production builds; Frida detection.
- **Screen-record blocking on API < 34** via `WindowManager.LayoutParams.
  FLAG_SECURE` (already done) plus MediaProjection callbacks where
  available.
- **Zero-trust telemetry** — no PII in analytics events; all metric payloads
  go through a redaction layer.
- **Full accessibility audit** — TalkBack, switch access, and voice access
  paths all reviewed against WCAG 2.2 AA (compliance requirement in EU/UK
  under the European Accessibility Act).
- **SOC 2 / PCI DSS build pipeline** — reproducible builds, signed artefacts,
  provenance attestations (SLSA level 3+), and dependency SBOM published
  with every release.

The point of listing them explicitly: bank-grade posture is a program, not a
single library. The codebase is designed so each of these items slots in
without a rewrite — the biometric prompt is already Class 3, the network layer
is already isolated behind one interface, error surfacing is already typed,
and there's no on-disk state to migrate.

---

## PCI DSS · applicability & mapping

**Scope note.** This app moves *payouts* between merchant accounts — no
cardholder data (PAN, CVV, magnetic-stripe) crosses this codebase. Strictly
speaking, **PCI DSS applies to card data**, not to bank-account transfers, so
the app itself is out of PCI scope. It is included here because the design
uses the PCI DSS v4.0 controls as a rigour baseline — many fintechs voluntarily
align mobile clients to the same controls where the underlying platform
processes cards.

For a mobile client that *does* handle card data, the requirements that
land inside the app boundary are 2, 3, 4, 6, 8, 10, 11. This codebase's
current posture mapped against them:

| PCI DSS v4.0 · req | Applies to a mobile client as | Status in this codebase |
|---|---|---|
| **2** Secure defaults | No debug flags in release, `usesCleartextTraffic="false"`, minSdk pinned | ✅ debug logging only, no cleartext, minSdk 34 |
| **3** Protect stored account data | No PAN / CHD stored, no unencrypted cache, no analytics egress | ✅ no on-disk state, no SDK data leaks |
| **4** Protect data in transit | TLS-only, strong cipher suites, certificate pinning | 🟡 TLS enforced by OkHttp defaults · pinning is a roadmap item |
| **6** Secure development | Static analysis, dependency scanning, no unsafe reflection | 🟡 lint + KSP · SBOM + Dependabot pending |
| **8** Identify + authenticate | Biometric SCA, no shared credentials, session bound to auth | ✅ Class 3 biometric on material payouts · session nonce is roadmap |
| **10** Log + monitor | No PII in analytics, redaction layer at telemetry boundary | ✅ zero third-party analytics · logs stripped in release |
| **11** Test security | Regular pen-test, static analysis in CI, dependency CVE scan | 🟡 unit tests + `assembleDebug` clean · CI security jobs pending |

Legend: ✅ shipped in this repo · 🟡 on the roadmap above · red items would
be blockers if this were the actual production build.

### Adjacent regimes worth naming

- **PSD2 · Strong Customer Authentication (SCA).** Biometric step-up on
  ≥ £1,000 is the SCA "inherence" factor. Combined with device possession
  it's already 2-of-3 factors. Full compliance would add a knowledge factor
  fallback (PIN) and dynamic linking of the auth token to amount + payee.
- **Open Banking / PSD2 Payment Initiation.** The payout request already
  carries a stable `device_id`; extending to a full JWS-signed payment
  initiation request is the roadmap item under *Request signing*.
- **UK FCA CP19/28 · Operational Resilience.** No third-party SDKs = no
  vendor outage risk in the client path.
- **GDPR / DPA 2018.** No PII on disk, no third-party analytics, no
  cross-border data egress — data-minimisation by construction.
- **European Accessibility Act 2025 (EAA).** WCAG 2.2 AA compliance is
  called out in the roadmap; mandatory for financial services in the EU
  from June 2025.

The one-line answer to *"is this PCI DSS compliant?"*: **the client-side
controls that a PCI DSS Level 1 program would require are either shipped or
on the mapped roadmap; the rest are server + operations + audit concerns
that live outside a mobile codebase.**

---

## Testing approach

Pure domain code is exercised in isolation with no Android or Robolectric
dependencies:

- `IbanValidatorTest` — mod-97 boundaries, typo detection, country coverage
- `AmountParserTest` — locale variance (en-GB dot, fr-FR comma), decimal cap
- `PayoutViewModelTest` — success, insufficient funds, double-tap idempotency,
  uses `StandardTestDispatcher` for coroutine control
- `DateLabelProviderTest` — freezes `Clock.fixed(instant, zone)` to prove
  timezone-local date labels ("Yesterday" from Los Angeles's perspective when
  the payout was 22:00 UTC — classic banking date-boundary bug)

`Clock` and `ZoneId` are injected specifically so tests don't depend on the
CI machine's wall clock or timezone.

---

## Run it

```bash
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:assembleDebug        # build a debug APK
```

Open in Android Studio (Ladybug or newer). Run the `app` configuration on any
API 34+ emulator or device. No backend to configure.

**Test the error triggers:**
- Amount `999.99` → mock returns 503 · UI shows "Service unavailable"
- Amount `888.88` → mock returns 400 with `INSUFFICIENT_FUNDS` code

---

## Where it ends and production begins

Owning the gaps is part of the design. These are the deliberate next steps for
a shipped version:

- `SavedStateHandle` on the payout form so an OS kill mid-flow doesn't lose
  the user's typing
- `Idempotency-Key` header on the payout POST wired end-to-end (client + server)
  so a retry after a network blip doesn't charge twice
- Local cache (Room) for offline balance reads so the merchant sees yesterday's
  number when the connection is flaky
- Retry with exponential backoff on transient network failures
- Baseline profile for cold-start performance
- Snapshot tests (Paparazzi) on the design-system layer
- Full accessibility audit — TalkBack labels, semantics groupings, live regions
  on status changes
- Real design tokens from the brand system instead of the placeholder purple

---

## Read order if you're browsing the code

1. `AppModule.kt` — all Hilt wiring in one file
2. `data/Repositories.kt` — three repos + the paging source
3. `ui/payout/PayoutViewModel.kt` + `PayoutConfirmResult.kt` — the payout flow
4. `security/` — biometric gate and screen security
