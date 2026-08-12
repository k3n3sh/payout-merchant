# Merchant Payout

Android take-home for Checkout.com. Merchant sees their balance and recent
activity, taps into the full transactions list, and can send a payout with
biometric step-up for larger amounts.

## Run it

1. Open the project in Android Studio (Ladybug or newer works).
2. Sync gradle, wait for KSP to finish.
3. Run the `app` config on any emulator or device on API 34+.

No backend to set up. A `MockWebServer` is bundled and starts inside the
`Application` class, so the app talks to a local mock over http on a random
port.

Requirements: `compileSdk 35`, `minSdk 34`, JDK 17.

## What's in

- Home screen with available + pending balance and last few activity rows.
- Full transactions modal with cursor-based paging (Paging 3), date group
  headers, and pull-to-refresh via a shared refresh signal after each payout.
- Payout form → confirm → result flow. IBAN mod-97 validation with country
  length table, locale-aware amount parsing straight into minor units so
  there's no float drift.
- Class 3 (`BIOMETRIC_STRONG`) step-up for any payout of £1,000 or more.
- Payout screens set `FLAG_SECURE`, register the API 34 screen-capture
  callback, and show a small banner if a capture attempt happens.
- Custom `CheckoutTheme` design system via `CompositionLocal` (colours,
  typography, shapes).
- 8 unit tests across IBAN, amount parsing, and the payout ViewModel.

## What's skipped on purpose

- `SavedStateHandle` on the payout form. Brief doesn't test process death.
- Room / offline cache. Mock backend is the source of truth here.
- `Idempotency-Key` header on the payout POST. Mock ignores it, didn't want
  to fake half a story.
- CI / lint config, baseline profile, proguard tuning.

## Read order

If you want the fastest tour of the code:

1. `AppModule.kt` — Hilt wiring, one module.
2. `data/Repositories.kt` — three repos + the paging source.
3. `ui/payout/PayoutViewModel.kt` + `PayoutConfirmResult.kt` — the payout flow.
4. `security/` — biometric gate and screen security.

## Tests

```
./gradlew :app:testDebugUnitTest
```
