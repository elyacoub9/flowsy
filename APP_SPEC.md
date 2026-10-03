# App Spec: Flowsy (v2, simplified)

## Purpose
A simple, private, offline budget app for Android. The year is shown as **12 calendar months**.
In each month you add income (salary, etc.) and expenses (by category) and see what is left.
Savings is a separate place where you add and remove money by hand.

Shared as a signed APK with friends and family. Each person sets their own currency, categories
and language.

v2 replaces the payday-cycle design (v1). Phases 1–5 of v1 are built (Phase 5 added pots, DB v5); Phase S below simplifies
the existing app and migrates its data. Nothing from v1 that is not listed here should remain.

## Core principles (never break these)
1. **Totals are computed, never stored.** Month income, spent, left, year totals, category totals
   and pot balances are always calculated from records. No running-total columns.
2. **Money is a `Long` in minor units.** Never `Double` or `Float` for money. Parse input with
   `BigDecimal`. Decimals come from `java.util.Currency.defaultFractionDigits`.
3. **Dates are stored as epoch days** (`LocalDate.toEpochDay()`). A record belongs to the
   calendar month of its date. **Months are not stored**; they are derived from dates.
4. **Safe migrations only.** `exportSchema = true`, schema JSON committed. Every schema change
   gets a version bump, a written `Migration` and a migration test. Never
   `fallbackToDestructiveMigration`.
5. **Every user-visible string exists in English and French.** No hardcoded text.
6. **Color is never the only signal.** Every colored state also has text, an icon or a sign.
7. **Savings never affect a month's Left.** Savings is fully separate.

## Tech stack (fixed, do not change without asking)
- Kotlin, Jetpack Compose, Material 3, dark theme only, Navigation Compose
- Room (KSP), settings in a single-row Room table
- Single Activity, ViewModel + StateFlow, repositories, pure-Kotlin `domain` package for all
  calculations (no Android imports, fully unit-tested)
- In-app language: `AppCompatDelegate.setApplicationLocales`
- Charts: drawn by hand with Compose `Canvas`. No chart library.
- Backup: `.zip` with JSON via `org.json`. File access via Storage Access Framework.
- minSdk 26, targetSdk/compileSdk latest stable, debug `applicationIdSuffix ".debug"`
- **No INTERNET permission. No backend, accounts, analytics, ads or tracking.**

## Key definitions

### Month
- A calendar month: 1st to last day. Identified in code as `YearMonth`, never stored.
- Per month:
```
income = Σ income records dated in the month
spent  = Σ expenses dated in the month
left   = income − spent
```
- `left` can be negative: shown as "Over by X" (#FF5F7E + icon + text).
- No carry-over between months.
- Spent ratio = spent / income (shown as a bar and "X% of income spent"; if income is 0 and
  spent > 0, show "No income this month").

### Year
- Year totals = sums of the 12 months. Year switcher goes from the earliest year that has a record
  (or the install year) to the current year.
- Future months of the current year are shown dimmed with no amounts.

### Dates
- Any past date and today are allowed. No future dates. One domain rule, `RecordDateRule`, used
  by every date picker and every repository.

### Income
- Record: label, amount (> 0), date, optional note.
- Add sheet offers quick label chips: Salary, Freelance, Bonus, Other (translated), plus free text.
  Chip order: labels used most in the last 90 days first.

### Categories (expenses)
- Built-in: Groceries, Restaurants, Transport, Bills, Health, Shopping, Entertainment, Home, Other.
  Built-ins store a translation key and display in the current language unless renamed.
- Add, rename, recolor, change icon, reorder, archive. "Other" can never be archived or deleted.
- A category with expenses cannot be deleted, only archived. Archived categories are hidden from
  chips but still shown on their expenses and in charts.
- No limits (removed in v2).

### Expenses
- Amount (> 0), category, date (default today), optional note. Edit and delete (Undo snackbar).

### Savings (separate)
- Pots: name, color key, icon, optional goal amount, optional goal date, opening balance,
  archived flag, sort order.
- Movements: DEPOSIT or WITHDRAWAL, amount, date, optional note. Manual only.
- Balance = opening balance + deposits − withdrawals. It can never go below 0 after any add,
  edit or delete (checked on the total, in the same transaction).
- Goal progress: "X of Y, Z%", ring or bar capped at 100%, "Goal reached" when balance ≥ goal,
  "Goal date passed" when the date is past and the goal not reached.
- A pot can be deleted only when its balance is 0 and it has no movements; otherwise archive.
- Savings totals: sum of all non-archived pot balances.

### Currency
- One currency per install, chosen at onboarding (phone region default). Can be changed only while
  no income, expense or pot records exist.

## Data model (Room)
- **Settings** (id = 1): currencyCode, languageTag, onboardingDone
- **Category**: id, name?, builtInKey? (unique), icon, color, archived, sortOrder
- **Expense**: id, amount, categoryId (RESTRICT), date, note?, createdAt
- **Income**: id, label, amount, date, note?, createdAt
- **Pot**: id, name, color, icon, goalAmount?, goalDate?, openingBalance, archived, sortOrder
- **PotMovement**: id, potId (RESTRICT), type (DEPOSIT / WITHDRAWAL), amount, date, note?,
  createdAt
- Indexes on every `date` column and every foreign key.

## Screens
Bottom navigation: **Month, Year, Savings**. Settings from the top bar.

- **Onboarding**: language, currency. Done.
- **Month** (opens on the current month; ◀ ▶ to change month):
  - Big **Left** number (#FFD166) or "Over by X".
  - Income and Spent tiles with amounts.
  - Bar: "X% of income spent".
  - **Interactive donut** of spending by category (no legend): total spent in the center by
    default; tapping a slice selects it and shows its icon, name, amount and % in the center;
    tapping it again or the center goes back to the total. Hint under the chart: "Tap a slice to
    see the category". Every slice is also selectable with TalkBack.
  - List of income and expenses grouped by day with day totals; income shown with "+".
  - Floating "+" opens a choice: Expense or Income. Expense sheet: amount focused with number
    keyboard, category chips (most-used first), date chip, note, Save. Income sheet: amount,
    label chips, date, note.
  - Tap a record to edit or delete (Undo).
- **Year** (year switcher at the top):
  - Year totals: income, spent, left.
  - **Bar chart**: 12 months, income vs spent side by side, value labels, current month marked.
  - List of 12 month cards: month name, income, spent, left (or "Over by"), and a thin spent/income
    bar. Tap opens that month.
- **Savings**: total savings at the top; pot cards with icon, name, balance, goal ring/bar and
  text; "+" to add a pot; "Archived" section. Pot detail: balance, goal, movement history (+/−),
  Deposit, Withdraw, Edit, Archive/Restore, Delete.
- **Settings**: language, currency (locked when records exist), categories, backup, restore,
  about.

## Backup and restore
- **Backup** (`flowsy-backup-YYYY-MM-DD.zip`): `meta.json` (app version, DB schema version,
  export date) + `data.json` (all tables including settings).
- **Restore**: validate, show the file's date and record counts, confirm "Replace all data", wipe
  and insert in one transaction. Refuse backups from a newer schema version or an unknown format.

## Build phases (one at a time, test on phone before moving on)

### Phase S: Simplify and migrate (DB v5 → v6)
- Remove: cycles, cycle engine, payday and weekend settings, income sources and entries, extra
  income, fixed charges and entries, category limits and warnings, reminder settings, Plan screen.
- Data migration, in one migration:
  - IncomeEntry RECEIVED → Income (label = source name, amount = received amount,
    date = received date). EXPECTED and SKIPPED are dropped.
  - ExtraIncome → Income (label, amount, date, note).
  - ChargeEntry PAID → Expense in the built-in "Bills" category (amount, date = paid date,
    note = charge name). UNPAID and SKIPPED are dropped.
  - Add built-in "Bills" (also seeded on fresh installs).
  - Settings: keep currencyCode, languageTag, onboardingDone; drop the rest.
  - Categories: drop limitAmount.
  - Pots and movements: kept, reshaped to the v2 model (pots gain icon, lose autoAmount;
    movements lose source and cycleId). AUTO and LEFTOVER movements become plain DEPOSITs.
  - Drop the old tables (children first, RESTRICT).
- Onboarding: language and currency only.
- Month screen basic version: Left, Income and Spent tiles, record list, add/edit income and
  expenses. Bottom nav: Month, Year (placeholder), Savings (placeholder).

### Phase M: Month visuals
Spent bar, donut chart with legend, day grouping polish, month switcher.

### Phase Y: Year
Year screen, year switcher, totals, 12-month bar chart, month cards.

### Phase P: Savings
Pots, movements, balance rule, goals, archived section.

### Phase B: Backup and restore

### Phase R: Release
App icon, launcher name, release signing with its own keystore, signed APK.

## Validation test cases

### Month boundaries (unit tests)
- An expense dated 31 Oct 2026 belongs to October; 1 Nov 2026 to November.
- February 2028 has 29 days; February 2026 has 28.
- A future date is refused; today is accepted.

### Money (unit test), currency MAD, October 2026
Income:
- Salary 12,350.00 (1 Oct), Refund 200.00 (10 Oct) → **Income = 12,550.00**

Expenses:
- Rent 4,500.00 (Bills), Electricity 342.50 (Bills), Groceries 820.40, Transport 150.00,
  Restaurants 389.60, Groceries 640.00 → **Spent = 6,842.50**

**Left = 12,550.00 − 6,842.50 = 5,707.50**. Spent ratio 54%.
Category totals: Bills 4,842.50, Groceries 1,460.40, Restaurants 389.60, Transport 150.00.

Variations:
- Add an expense of 6,000.00 → Left = −292.50 → "Over by 292.50"
- Savings activity does not change Left.

Savings:
- Emergency: opening 10,000.00, deposit 1,000.00 → 11,000.00
- Travel: deposit 500.00, withdrawal 300.00 → 200.00
- Withdraw 250.00 from Travel → refused (balance 200.00)
- Total savings = 11,200.00

Year 2026 (with September: Salary 12,000.00, expenses 9,500.00):
- Year income 24,550.00, spent 16,342.50, left 8,207.50.

## Visual style
- Dark theme only. Background #0F1014, cards #1A1B22.
- Semantic colors: Income #2EDB9A, Spent #FF8A5C, Left #FFD166, Over #FF5F7E, Savings #7B8CFF.
- Category palette: 10 vivid colors, readable on #1A1B22.
- Big bold numbers, rounded cards, generous spacing, 48 dp touch targets.
- Charts always show amounts as text (the donut center when a slice is tapped, the Year chart's
  value labels) and have a content description listing every value; color is never the only signal.
- Numbers and dates formatted for the current app language.

## Out of scope
Payday cycles, recurring bills, budgets/limits, reminders, Excel export, accounts, cloud sync,
bank connections, multiple currencies, widgets, Play Store, iOS, ads, in-app purchases.

## Testing checklist per phase
- Runs on the Samsung phone via USB
- `gradlew.bat test` passes; instrumented tests pass with
  `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
- Force-close and reopen: all numbers unchanged
- Switch language EN ↔ FR: every visible string translated, numbers formatted correctly
