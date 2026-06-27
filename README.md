# Polish Passive Vocabulary Size Test

Small ClojureScript starter using Shadow CLJS and Reagent.

## Development

Install project dependencies:

```sh
npm install
```

Start the Reagent browser build, dev server, and Shadow nREPL:

```sh
npm run dev
```

Open <http://localhost:8000/index.html>. The dev script watches Tailwind CSS and Shadow CLJS; Shadow starts its nREPL on port `7888`.

For Calva jack-in, use the `:shadow` alias. Calva injects its own nREPL/CIDER deps with `-Sdeps`, then runs Shadow.

Use that REPL for quick probes while the app is running. For example, `jamiepratt.vocab-test-client-side.core/app-state` is public so you can inspect or tweak Reagent state during debugging, but automated tests stay browser-first.

Compile once:

```sh
npm run compile
```

## Local Database

Start local PostgreSQL, then build/import the SUBTLEX bundle:

```sh
npm run db:local:setup
```

Default DB URL is set in `.env`: `postgresql://localhost:5432/vocab_test_client_side`. Copy `.env.example` if `.env` is missing. Shell env vars override `.env`, so one-off overrides still work: `DATABASE_URL=... npm run db:local:setup`.

The script creates the DB if missing, builds `target/local-polish-lexicon-bundle`, applies migrations, imports with `--replace`, then verifies row counts.

## Production Database

Use Neon Postgres in AWS Frankfurt (`eu-central-1`) with autosuspend enabled. Keep two URLs:

- pooled URL for Fly runtime: `fly secrets set DATABASE_URL='<pooled Neon URL>' -a vocab-test`
- direct URL for migrations/admin: `DATABASE_URL='<direct Neon URL>' clojure -M:db migrate`

Do not import lexicon data into production unless a valid import bundle has been intentionally selected.

## Tooling

Required for normal development: Java, the Clojure CLI (`clojure`), Node.js, and npm. On macOS, prefer the official/Homebrew installs:

```sh
brew install --cask temurin@25
brew install clojure/tools/clojure
```

Use the [Node.js download page](https://nodejs.org/en/download) or a Node version manager for Node/npm.

Recommended for full local verification:

```sh
brew install borkdude/brew/clj-kondo
npx playwright install chromium
```

`clj-kondo` is required by `npm run verify`. Playwright's Chromium browser binary is required for E2E tests. `clojure-lsp` is optional, but recommended for editor diagnostics, definitions, references, and clean namespace checks:

```sh
brew install clojure-lsp/brew/clojure-lsp-native
```

If you do not use Homebrew, see the official install docs for [Clojure CLI](https://clojure.org/guides/install_clojure), [clj-kondo](https://github.com/clj-kondo/clj-kondo#installation), [clojure-lsp](https://clojure-lsp.io/installation/), and [Playwright browsers](https://playwright.dev/docs/browsers).

## Tests

Treat browser-visible behavior as the main public interface. Prefer semantic Playwright tests using `getByRole`, `getByLabel`, and visible text. Keep the REPL for debugging/probing, not as the test runner of record.

Run fast semantic browser tests against the dev build:

```sh
npm run test:e2e
```

Run the same smoke flow visibly:

```sh
npm run test:e2e:headed
```

Compile the isolated release build and smoke-test it:

```sh
npm run test:e2e:release
```

Run the full confidence gate:

```sh
npm run verify
```

`verify` runs `clj-kondo`, a dev compile, dev E2E tests, and release E2E tests. Dev output writes to `public/js` and `public/css/app.css`; release output writes to `target/release/public`. Browser tests are Chromium-first; add mobile and broader browser coverage later when the app needs it.
