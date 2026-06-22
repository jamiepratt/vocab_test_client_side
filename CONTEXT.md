# Vocab Test Client Side Context

This file is the repo-level context for `grill-me-with-docs`. Treat it as the default source for development and testing decisions unless nearby code contradicts it.

## Use During Grilling

- Read this file before asking development workflow questions.
- Do not ask about decisions already fixed here unless the user's plan would change them.
- Challenge plans that make tests depend on a live REPL, preserved browser state, implementation details, or visual snapshots.
- Check nearby implementation when present: `package.json`, `shadow-cljs.edn`, `public/index.html`, `src/`, `test/`, `tests/`, `e2e/`, and `playwright.config.*`.
- If code and this file disagree, call out the contradiction and ask which source should win.

## Development Defaults

- Use Shadow CLJS with two build labels:
  - `:dev` for local watch mode and the development server.
  - `:release` for optimized release smoke testing.
- Keep `npm run dev` as the main local development command.
- Serve the dev app at <http://localhost:8000/index.html>.
- Run Shadow nREPL on port `7888`.
- Use the REPL as a development accelerator, not as the test runner of record.
- Use the REPL for quick evals, state inspection, state pokes, and debugging.
- Keep automated tests reproducible without requiring an already-connected REPL.

## App Defaults

- Use Reagent so the project demonstrates realistic CLJS frontend state and rendering.
- Keep `jamiepratt.vocab-test-client-side.core/app-state` public so it can be inspected or tweaked from the REPL.
- Keep browser tests DOM-first and user-visible, even though REPL access exists.

## Browser Testing Defaults

- Use plain Playwright for end-to-end tests.
- Do not write Playwright tests in CLJS for now.
- Prefer semantic browser tests over implementation-detail tests.
- Assert visible headings, labels, buttons, text, and user-visible state.
- Prefer Playwright locators such as `getByRole`, `getByLabel`, and visible text.
- Avoid visual snapshot testing for now.

## Iteration Loop

- Run `npm run dev` once and keep it running.
- Let Shadow CLJS automatically recompile after file changes.
- Use the REPL for quick evals, state inspection, and state pokes.
- Use Chrome browser control as the microscope for current app behavior.
- Inspect the same live browser state in Chrome after recompilation.
- Run `npm run test:e2e` repeatedly during development.
- Use Playwright as the tripwire for repeatable browser regressions.
- Add or update Playwright tests once the desired behavior is understood.

## Chrome Browser Control

- Plans may use the `@chrome` browser control extension for live inspection of the running app.
- Use Chrome browser control to inspect DOM, screenshots, and current browser state after REPL pokes or Shadow hot reloads.
- Do not treat Chrome inspection as a regression test; convert stable findings into Playwright tests.

## State Boundaries

- Preserved app state during hot reload is useful for fast iteration.
- Preserved state is not proof that startup, reload, or release behavior works.
- Playwright and release smoke tests are responsible for clean-load behavior.
- Playwright should reuse the existing Shadow dev server when it is already running.

## Release Smoke Testing

- Keep release output isolated from the dev build.
- The `:dev` build writes to `public/js`.
- The `:release` build writes to `target/release/public/js`.
- Release smoke testing copies `public/index.html` to `target/release/public/index.html`.
- Release smoke tests serve `target/release/public` on `localhost:4173`.
- Dev and release tests share the same semantic smoke flow.

## Static Checks

- Treat `clj-kondo` as a required project check.
- Use `npm run lint:clj` for Clojure, ClojureScript, and EDN linting.
- Include `clj-kondo` in the full verification command.
- `clj-kondo` must be installed on `PATH` for `npm run verify` to complete.

## Verification Gate

- Use `npm run verify` as the confidence gate before handoff, commit, or push.
- `verify` should run:
  - `npm run lint:clj`
  - `npm run compile`
  - `npm run test:e2e`
  - `npm run test:e2e:release`

## Current Non-Goals

- No visual regression testing yet.
- No TypeScript or ESLint verification gate yet.
- No mobile Playwright projects yet.
- No backend/API fixtures yet.
- No broad test-only browser globals.
- No helper script for copying release HTML.

## Good Grill-Me Questions

- Should this change alter a fixed default, or fit inside the existing workflow?
- Is the REPL being used only to accelerate development, while tests remain reproducible?
- Does Chrome inspection explain current behavior, and does Playwright capture the lasting regression guard?
- Does the plan prove both preserved-state iteration and clean-load startup behavior?
- Are any new tools, browser projects, snapshots, fixtures, or globals justified enough to leave the current non-goals list?
