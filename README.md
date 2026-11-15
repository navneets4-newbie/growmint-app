# Grow Mint

Grow Mint: an AI money coach prototype that helps people manage money and shows them how to improve it.

## Status

**Prototype.** Grow Mint is an educational personal finance tool and virtual ledger prototype. It is not a launched financial service, has no live banking integration enabled, and does not provide professional financial advice.

## The problem it addresses

Most expense trackers show charts and past spending data, but they do not tell you what to fix next or help you act safely on what you earn. Grow Mint pairs a local virtual ledger and quick transaction entry with deterministic math checks, safety-first guardrails, and plain-English AI explanations so users can see their financial health score, compare concrete payoff and savings paths, and track month-to-month progress.

## Key features

### Setup and profile
- **6-step setup wizard** (`OnboardingScreen`): collects currency, country, life stage (`Student`, `Early career`, `Family`, `Self-employed`, `Retired`), income type (`Steady`, `Irregular`, `Allowance`, `None`), starting balance, monthly expenses, debts, target goals, risk comfort level, and weekly side-income hours.
- **Any-currency support & manual secondary currency**: supports multiple ISO 4217 currencies (`CurrenciesAndCountries`) and optional secondary-currency display using a user-entered exchange rate (no live exchange-rate API calls).
- **Modes & themes**: includes Guided Mode (Under 18), Student Mode, Local-only privacy mode, and **Day / Night / Auto** theme switching.

### Money tracking
- **Dashboard & cashflow overview** (`DashboardScreen`): shows virtual balance, monthly surplus/deficit ratio, 30-day tracked category totals, emergency buffer status, and quick income/expense dialogs.
- **Transactions screen & Quick-Add** (`TransactionsScreen`): log expenses or income in under 3 taps with amount, optional merchant/note, rule-based category suggestions, star-pinned favorite and recent categories, and a **Same as last time** repeat button.
- **Paste-to-import** (`PasteImportDialog`): paste bank SMS alerts, UPI messages, or copied statement lines; AI extracts structured JSON (`date`, `amount`, `type`, `merchant`, `suggestedCategory`) after PII sanitization and rejects malformed rows in code.
- **CSV statement import & column mapper** (`CsvImportAndMappingDialog`): select a CSV file via Android's Storage Access Framework (or paste CSV rows), parse locally in Kotlin, map Date/Description/Amount or Debit+Credit columns, and optionally request AI category suggestions for unmatched merchants. Also supports CSV export in Settings.
- **Mandatory review screen** (`TransactionReviewScreen`): shows counts of found, skipped, and potential duplicate rows; lets you switch ambiguous dates between `DD/MM` and `MM/DD`; flags possible duplicates (unchecked by default); and lets you edit any field before tapping **Save selected**.
- **Smart category rules**: automatically learns `merchant -> category` rules when you fix a category in Review or Quick-Add, and lets you view, add, edit, or delete rules in Settings.
- **Batch undo & income detection**: **Undo this import** removes the exact imported batch and restores your virtual balance; imported income shows an **"Income received, ready to split it?"** prompt that opens Paycheck Auto-Divider pre-filled with that amount.
- **Paycheck Auto-Divider** (`SmartSplitScreen`): splits income by customizable percentages into virtual envelopes (`Essentials & Bills`, `Rainy Day Savings`, `Emergency Safety Net`, `Future Investments`, `Fun & Guilt-Free`, `Side Hustle & Skills`) with presets and undo support.
- **Pay-Yourself-a-Salary Holding Pot**: for irregular income users, tracks a holding pot balance, monthly salary releases, and months of runway.
- **Side-Hustle Profit Tracker** (`SideHustleTrackerScreen`): tracks gig income minus expenses and computes net profit, margin percentage, and best/worst months.
- **Subscription Leak Detector**: scans ledger and sandbox bank transactions for recurring subscription-style charges.

### Coaching and improvement
- **Financial Health Score & Monthly Improvement Report** (`FinancialCalculator`, `WeeklyCheckInAndLessonsScreen`): calculates a 0–100 score and letter grade (`A+` to `D`), month-over-month score delta, what improved or slipped, top 3 concrete actions, and saved monthly snapshots.
- **Weekly Money Score, Check-In & No-Spend Streak**: tracks consecutive no-spend days, weekly check-ins, and daily plain-English micro-lessons.
- **Friendly AI Money Coach** (`AiCoachScreen`, `AiCoachManager`): generates a Growth Plan (Protect, Save, Invest, Earn pillars) adapted to country, life stage, income type, and safety-buffer status—with a deterministic offline fallback plan if AI is unreachable—plus a streaming AI chat tab.
- **Income Ideas Lab** (`IncomeIdeasLabScreen`): suggests realistic side-income paths matched to weekly available hours.

### Planning tools
- **Debt Payoff Planner** (`DebtPlannerScreen`): compares Snowball (smallest balance first) vs. Avalanche (highest interest first) side-by-side with payoff date, total interest, interest saved, optional extra monthly payment, and a monthly schedule.
- **Goal Planner** (`GoalPlannerScreen`): tracks House, Car, Trip, Retirement, and Custom goals with remaining amounts, months left, monthly savings needed, and on-track status.
- **Net Worth & Trend** (`NetWorthScreen`): combines manual assets (`Cash`, `Savings`, `Investments`, `Property`, `Other`), virtual ledger cash balance, and debts, with saved monthly snapshots and a trend canvas chart.
- **SIP & What-If Reality Check** (`WhatIfAndScamScreens`): compares Poor, Typical, and Good compounding outcomes side-by-side plus worst-year drop estimates.
- **Deadline & Renewal Calendar** (`DeadlineCalendarScreen`): tracks bills, exam fees, and renewals over the next 30 days with "Due soon" alerts.
- **Practice Trading Game** (`PaperTradingScreen`): zero-risk simulated market sandbox with virtual play cash.

### Safety
- **Eligibility Gate & Guided Mode (Under 18)**: learning-only mode that hides investing splits, Practice Trading Game, Debt Payoff Planner, and What-If investing tools, and routes investing split percentages into Savings.
- **Student Mode**: adapts coach framing for student schedules and expenses.
- **Safety-First Emergency Buffer Gate**: checks progress toward an emergency buffer (default 1 month of typical spending, user-editable) and locks investing suggestions/allocations until met.
- **Scam & Fraud Checker** (`ScamCheckerScreen`): analyzes suspicious SMS, WhatsApp/Telegram, UPI, or job-fee messages for red flags and calm next steps, reminding users never to share OTP, PIN, or passwords.
- **App PIN Lock & Full Data Reset**: optional 4-digit PIN lock (`AppLockOverlay`) and a **Delete all my data** button in Settings.

## How it works

Grow Mint follows a four-step improvement loop:
1. **Understand**: Collect your profile (country, currency, life stage, income type, expenses, debts, goals) and recent transactions via Quick-Add, Paste-to-Import, or CSV import.
2. **Score**: Compute your 0–100 Financial Health Score, Weekly Money Score, cashflow surplus/deficit, and emergency buffer progress.
3. **Improve**: Review your Monthly Improvement Report's top 3 actions, compare Debt Snowball vs. Avalanche schedules, check monthly savings needed per goal, run Paycheck Auto-Divider rules, and read plain-English guidance from the AI Money Coach.
4. **Track**: Log no-spend streaks, weekly check-ins, 30-day spending categories, and monthly snapshots for Net Worth and Health Score.

**Core design rule — numbers come from code, not the AI:**
All financial math (scores, splits, buffer gates, debt schedules, SIP simulations, profit margins, net worth, CSV parsing, duplicate checks, and counts) is computed deterministically in Kotlin (`FinancialCalculator`, `TransactionImportEngine`, `GrowMintRepository`). The AI (`AiCoachManager` using `gemini-3.8-flash` via Firebase AI Logic) is used only to explain concepts/results in plain English, assess scam red flags, or extract/categorize transactions as structured JSON.

## Safety and privacy choices

- **Eligibility gate & Guided Mode**: Under-18 users are placed in education-only Guided Mode with investing and paper-trading features hidden.
- **Buffer-first rule**: Adult users must meet their emergency safety buffer target before investing guidance, What-If simulations, or investing envelope splits unlock.
- **Confirmation before changes**: Destructive and money-changing actions pass through confirmation dialogs (`ConfirmActionDialog`), and imported transactions always open in `TransactionReviewScreen` before anything is saved.
- **No real money movement**: All balances, envelopes, and trades live in an offline virtual ledger. Grow Mint never asks for bank passwords, OTPs, or PINs and cannot initiate payments or transfers.
- **Local-only privacy mode & PII sanitization**: Local-only mode (`localOnlyMode`) is ON by default so personal data stays on-device in `SharedPreferences` and AI coaching prompts withhold exact balances and names. Before sending pasted text or merchant descriptions to AI for extraction/categorization, `TransactionImportEngine.sanitizeForAi` strips account numbers, card numbers, phone numbers, OTPs/PINs, emails, UPI IDs, and customer salutations, and raw pasted text is never stored.

## Tech stack

- **Android app**: Kotlin (`2.2.10`), Android Gradle Plugin (`9.1.1`), `compileSdk 36`, `minSdk 24`, `targetSdk 36`
- **UI**: Jetpack Compose (BOM `2024.09.00`), Material Design 3, Material Icons Extended, Lifecycle Runtime/ViewModel Compose (`2.8.7`), Activity Compose (`1.10.1`)
- **AI & App Integrity**: Firebase BoM (`34.17.0`), Firebase AI Logic (`firebase-ai`, `gemini-3.8-flash` with structured JSON schemas), Firebase App Check (`firebase-appcheck-recaptcha` & `firebase-appcheck-debug`), Secrets Gradle Plugin (`2.0.1`)
- **Local Storage**: Android `SharedPreferences` + `org.json` *(Note: Room, Retrofit, OkHttp, and Moshi dependencies are present in `app/build.gradle.kts` from the project template, though active persistence uses `SharedPreferences` + `org.json`)*
- **Testing**: JUnit (`4.13.2`), Robolectric (`4.16.1`), AndroidX Test, Kotlinx Coroutines Test (`1.10.2`)
- **Optional backend prototype (`/backend`)**: Node.js, Express (`^4.19.2`), `plaid` (`^25.0.0`), `helmet`, `cors`, `express-rate-limit`, `cookie-parser`, `dotenv` (standalone prototype, not wired to the Android client)

## Setup and run instructions

### Prerequisites
- Android Studio (with JDK 17+ / Android SDK 36; *TODO: confirm* minimum Android Studio version)
- A Firebase project configured with **Firebase AI Logic** (for `app/google-services.json` and App Check)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/) if you plan to configure direct Gemini API usage or custom backend/environment integrations
- Node.js 18+ (only if running the optional `/backend` prototype)

### 1. Clone and configure environment variables
```bash
git clone <your-repo-url>
cd <repo-folder>
cp .env.example .env
```

Add your Gemini API key and any optional signing/debug values to `.env` (or export them in your shell environment):
```bash
export GEMINI_API_KEY="your_actual_gemini_api_key"
```

> **Note on how AI is currently wired:** In the current Android code (`AiCoachManager.kt` and `AppCheckHelper.kt`), Gemini calls go through **Firebase AI Logic** (`Firebase.ai`) backed by `app/google-services.json` and Firebase App Check rather than reading `GEMINI_API_KEY` directly in Kotlin. Keeping `GEMINI_API_KEY` in `.env` prepares the project if you switch or add direct Gemini SDK/REST calls via the Secrets Gradle Plugin (`BuildConfig`).

If you are building locally in debug mode with Firebase App Check enforcement enabled on your Firebase project, pass your Firebase App Check debug token via an Intent extra (`FIREBASE_APPCHECK_DEBUG_TOKEN`) or register the generated debug secret in the Firebase Console. For release builds, set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` in your environment.

### 2. Build and run unit tests (Android)
> *Note:* This repository includes `gradle/wrapper/gradle-wrapper.properties`, without a `gradlew` launcher script (*TODO: confirm* whether you want to generate `./gradlew` with `gradle wrapper`). Run commands using `gradle` directly:

```bash
# Run unit and Robolectric tests
gradle :app:testDebugUnitTest

# Build the debug APK
gradle :app:assembleDebug
```

Or open the root folder in Android Studio, sync Gradle, and run the `:app` configuration on an emulator or Android device (API 24+).

### 3. Optional standalone backend prototype (`/backend`)
```bash
cd backend
npm install
cp .env.example .env
# Edit backend/.env with your own TOKEN_ENCRYPTION_KEY, SESSION_SECRET, and Plaid Sandbox keys
npm start
```

## Screenshots

*Add screenshots to `docs/screenshots/` before publishing:*

- ![Home screen](docs/screenshots/home.png)
- ![Transactions and import review](docs/screenshots/transactions_review.png)
- ![Paycheck auto-divider](docs/screenshots/paycheck_split.png)
- ![AI money coach](docs/screenshots/ai_coach.png)
- ![Debt and goal planners](docs/screenshots/planners.png)

## Known limitations

- **No live bank linking**: Settings displays a disabled **Link bank (coming later)** card and the bank connect/refresh handlers in `MainActivity.kt` are no-ops. Both `SandboxBankProviderAdapter` in Android and the Express server in `/backend` (which uses an in-memory `Map` instead of a database) are disconnected prototypes.
- **Device-local storage only**: All ledger data, envelopes, goals, debts, and smart category rules are stored locally in unencrypted `SharedPreferences` (`growmint_virtual_ledger_prefs`). Uninstalling the app erases data unless exported via CSV, and there is no live account login or cloud sync.
- **AI features require network & Firebase configuration**: AI Growth Plan, AI Chat, Paste-to-Import, and CSV AI category suggestions depend on Firebase AI Logic (`google-services.json`) and App Check setup. When offline or unconfigured, Growth Plan and Scam Checker fall back to deterministic rule-based code logic, while Paste-to-Import shows an error with a manual-entry fallback.
- **Educational calculations only**: Health scores, SIP compounding projections, debt amortization estimates, and scam checks use simplified models and should not be treated as guaranteed forecasts.
- **Unused dependencies in build file**: `Room`, `Retrofit`, `OkHttp`, and `Moshi` are declared in `app/build.gradle.kts` even though the current prototype stores data with `SharedPreferences` and `org.json`.
- **Android instrumented tests**: Only unit and Robolectric tests are actively implemented (`FinancialCalculatorTest`, `TransactionImportAndRepoTest`); `ExampleInstrumentedTest` is the default template test.

## Roadmap

- Bank linking through a licensed provider
- Shareable score card

## Disclaimer

Grow Mint is an educational tool and does not provide professional financial, investment, or tax advice.

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
