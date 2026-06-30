# Project Codex Instructions

Be extremely concise, sacrifice grammar for concision.

## Clojure Tooling

- Use `clojure-lsp` when available for static Clojure/CLJS help: diagnostics, definitions, references, rename impact, symbols, and clean namespace checks.
- Prefer LSP before broad refactors or renames; use it to find references and stale/missing requires.
- Use LSP as file/project analysis only. Use nREPL, `npm run compile`, and browser E2E tests for runtime/compiler/browser truth.
- If `clojure-lsp` is unavailable, do not block; fall back to `rg`, lint, compile, tests, and nREPL probing.
- Use `clj-nrepl-eval --discover-ports` to find running nREPLs.
- Use `clj-nrepl-eval -p <port> "<form>"` for REPL evaluation and test probing.
- Editors like Calva often inject nREPL/CIDER deps with `-Sdeps` when jack-in starts Shadow. Do not add project aliases just for editor REPL tooling.
- If Codex must start Shadow watch and needs that REPL tooling, inject it in the command, e.g. `clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.0.0"} cider/cider-nrepl {:mvn/version "0.28.7"}}}' -M:shadow watch dev`.
- After editing Clojure or ClojureScript files, run `clj-paren-repair <changed-files>` before tests.
- Prefer `clj-paren-repair` over hand-editing unbalanced delimiters.

## Testing Rules

- Treat browser-visible behavior as the main public interface for app features.
- Prefer semantic Playwright tests in `tests/e2e`, especially shared flows in `tests/e2e/shared/appSmoke.ts`.
- Use user-visible locators: `getByRole`, `getByLabel`, and visible text.
- Do not add CLJS Playwright tests, visual snapshots, broad test-only browser globals, or implementation-detail assertions.
- Use the REPL only for debugging/probing, not as the test runner of record.

Fast loop:

```sh
npm run test:e2e
```

Useful narrow checks:

```sh
npm run lint:clj
npm run compile
```

Full confidence gate before handoff, commit, or push:

```sh
npm run verify
```

`npm run verify` runs Clojure/CLJS/EDN linting, dev compile, dev E2E tests, and release E2E smoke tests.

Run `npm run dev` once and keep it running during browser-test development. The app is served at <http://localhost:8000/index.html>; Shadow nREPL uses port `7888`.

`npm run test:e2e` serves an isolated dev E2E build at <http://localhost:8001/index.html>; keep Playwright off the long-running `8000` dev server.

When viewing the dev server in the browser, do not reload just to confirm a code change. Shadow auto-recompiles on file changes; rely on the live update unless there is specific evidence the browser state is stale.

Do not run `npm run compile` during browser dev-server iteration. The running Shadow watch already recompiles on file changes, and a separate compile can disrupt the hot-reload client used by the open browser tab. `npm run test:e2e` uses `target/e2e/public`, not `public/js`.

## Local SUBTLEX Notes

- When working in `data/subtlex-pl`, also read `data/subtlex-pl/AGENTS.local.md` if it exists. It is local-only and ignored by Git.
