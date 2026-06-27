import { spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadDotEnv } from "./load-env.mjs";

const EXPECTED_MANIFEST_SHA =
  "aa11facf36e6b7eb232dfe6b0a9a900f05407cd607efa5802bfd0d726fd93965";
const DEFAULT_BUNDLE_DIR = "db/import-bundles/polish-lexicon-import-v1";
const DEFAULT_IMPORT_PARENT = "9e82f19";
const FINAL_COUNTS = {
  example_sentences: 1830,
  lemma_pos_distractors: 7320,
  example_sentence_distractor_assignments: 0,
};

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");

loadDotEnv();

const options = parseArgs(process.argv.slice(2));
const bundleDir = normalizeRelativePath(options.bundleDir || DEFAULT_BUNDLE_DIR);
const manifestSha = options.manifestSha || EXPECTED_MANIFEST_SHA;
const databaseUrl = options.databaseUrl || process.env[options.envName || "DATABASE_URL"];

if (!databaseUrl) {
  fail(`Database URL is required; set ${options.envName || "DATABASE_URL"} or pass --database-url.`);
}

if (manifestSha !== EXPECTED_MANIFEST_SHA) {
  fail(`Manifest SHA input does not match the approved bundle SHA: ${EXPECTED_MANIFEST_SHA}`);
}

const manifestPath = path.join(root, bundleDir, "manifest.json");
if (!fs.existsSync(manifestPath)) {
  fail(`Bundle manifest is missing: ${bundleDir}/manifest.json`);
}

const actualManifestSha = sha256File(manifestPath);
if (actualManifestSha !== manifestSha) {
  fail(`Committed bundle manifest SHA mismatch: expected ${manifestSha}, got ${actualManifestSha}`);
}

const importCommit = options.importRef || findImportCommit(bundleDir, options.importParent);
const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), "legacy-db-import-"));
const importWorktree = path.join(tempRoot, "pre-normalization");
const clojureEnv = { ...process.env, DATABASE_URL: databaseUrl };

try {
  if (options.createDb) {
    ensureDatabaseExists(databaseUrl);
  }

  console.log(`Resetting database from ${options.envName || "--database-url"}.`);
  psql(databaseUrl, "DROP SCHEMA IF EXISTS polish_lexicon CASCADE;\nDROP TABLE IF EXISTS schema_migrations;\n");

  console.log(`Checking out pre-normalization import commit ${importCommit}.`);
  run("git", ["worktree", "add", "--detach", importWorktree, importCommit], { cwd: root });

  console.log("Applying pre-normalization schema.");
  run("clojure", ["-M:db", "migrate"], { cwd: importWorktree, env: clojureEnv });

  console.log("Importing legacy bundle with --replace.");
  run("clojure", ["-M:db", "import", bundleDir, "--replace"], {
    cwd: importWorktree,
    env: clojureEnv,
  });

  console.log("Migrating forward with current code.");
  run("clojure", ["-M:db", "migrate"], { cwd: root, env: clojureEnv });

  const counts = verifyFinalCounts(databaseUrl, manifestSha);
  console.log(`Verified final counts: ${JSON.stringify(counts)}`);
} finally {
  run("git", ["worktree", "remove", "--force", importWorktree], {
    cwd: root,
    allowFailure: true,
  });
  fs.rmSync(tempRoot, { recursive: true, force: true });
}

function parseArgs(args) {
  const parsed = {
    createDb: false,
    importParent: DEFAULT_IMPORT_PARENT,
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    switch (arg) {
      case "--bundle-dir":
        parsed.bundleDir = requireValue(args, ++index, arg);
        break;
      case "--database-url":
        parsed.databaseUrl = requireValue(args, ++index, arg);
        break;
      case "--env":
        parsed.envName = requireValue(args, ++index, arg);
        break;
      case "--import-parent":
        parsed.importParent = requireValue(args, ++index, arg);
        break;
      case "--import-ref":
        parsed.importRef = requireValue(args, ++index, arg);
        break;
      case "--manifest-sha":
        parsed.manifestSha = requireValue(args, ++index, arg);
        break;
      case "--create-db":
        parsed.createDb = true;
        break;
      case "--skip-create-db":
        parsed.createDb = false;
        break;
      default:
        fail(`Unknown argument: ${arg}`);
    }
  }

  return parsed;
}

function requireValue(args, index, flag) {
  const value = args[index];
  if (!value || value.startsWith("--")) {
    fail(`${flag} requires a value.`);
  }
  return value;
}

function normalizeRelativePath(value) {
  if (path.isAbsolute(value) || value.split(/[\\/]+/).includes("..")) {
    fail(`Path must be relative to the repo root: ${value}`);
  }
  return value;
}

function findImportCommit(bundleDir, importParent) {
  const manifestRelativePath = `${bundleDir}/manifest.json`;
  const output = runCapture(
    "git",
    ["rev-list", "--reverse", `${importParent}..HEAD`, "--", manifestRelativePath],
    { cwd: root },
  )
    .trim()
    .split(/\r?\n/)
    .filter(Boolean);

  if (output.length === 0) {
    fail(`Could not find a pre-normalization bundle commit for ${manifestRelativePath}.`);
  }

  return output[0];
}

function sha256File(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function ensureDatabaseExists(databaseUrl) {
  const db = parseDatabaseUrl(databaseUrl);
  const pgEnv = { ...process.env };
  if (db.password) pgEnv.PGPASSWORD = db.password;

  const psqlBaseArgs = ["-h", db.host, "-p", db.port];
  if (db.user) psqlBaseArgs.push("-U", db.user);

  const exists = runCapture(
    "psql",
    [
      ...psqlBaseArgs,
      "-d",
      "postgres",
      "-Atc",
      `select 1 from pg_database where datname = '${db.name.replaceAll("'", "''")}'`,
    ],
    { cwd: root, env: pgEnv },
  ).trim();

  if (exists === "1") {
    console.log(`Database ${db.name} already exists.`);
    return;
  }

  console.log(`Creating database ${db.name}.`);
  run("createdb", [...psqlBaseArgs, db.name], { cwd: root, env: pgEnv });
}

function parseDatabaseUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    fail("Database URL must be a postgres:// or postgresql:// URL.");
  }

  if (url.protocol !== "postgres:" && url.protocol !== "postgresql:") {
    fail("Database URL must use postgres:// or postgresql://.");
  }

  const name = decodeURIComponent(url.pathname.replace(/^\//, ""));
  if (!name) fail("Database URL must include a database name.");

  return {
    host: url.hostname || "localhost",
    port: url.port || "5432",
    user: url.username ? decodeURIComponent(url.username) : undefined,
    password: url.password ? decodeURIComponent(url.password) : undefined,
    name,
  };
}

function verifyFinalCounts(databaseUrl, manifestSha) {
  const sql = `
SELECT label, row_count
FROM (VALUES
  ('example_sentences', (SELECT count(*)::bigint FROM polish_lexicon.example_sentences)),
  ('lemma_pos_distractors', (SELECT count(*)::bigint FROM polish_lexicon.lemma_pos_distractors)),
  ('example_sentence_distractor_assignments', (SELECT count(*)::bigint FROM polish_lexicon.example_sentence_distractor_assignments)),
  ('legacy_distractor_columns', (
    SELECT count(*)::bigint
    FROM information_schema.columns
    WHERE table_schema = 'polish_lexicon'
      AND table_name = 'example_sentences'
      AND column_name LIKE 'distractor_%_translation'
  )),
  ('latest_manifest_matches', (
    SELECT CASE WHEN manifest_sha256 = '${manifestSha}' THEN 1::bigint ELSE 0::bigint END
    FROM polish_lexicon.import_manifests
    ORDER BY imported_at DESC, import_manifest_id DESC
    LIMIT 1
  ))
) AS counts(label, row_count);
`;
  const rows = runCapture("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1", "-At", "-F", "\t", "-c", sql], {
    cwd: root,
  })
    .trim()
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => line.split("\t"));
  const counts = Object.fromEntries(rows.map(([label, count]) => [label, Number(count)]));

  for (const [table, expected] of Object.entries(FINAL_COUNTS)) {
    if (counts[table] !== expected) {
      fail(`Final ${table} count mismatch: expected ${expected}, got ${counts[table]}`);
    }
  }
  if (counts.legacy_distractor_columns !== 0) {
    fail(`Legacy distractor columns still exist: ${counts.legacy_distractor_columns}`);
  }
  if (counts.latest_manifest_matches !== 1) {
    fail("Latest import manifest does not match the approved bundle SHA.");
  }

  return counts;
}

function psql(databaseUrl, sql) {
  runWithInput("psql", [databaseUrl, "-v", "ON_ERROR_STOP=1"], sql, { cwd: root });
}

function run(command, args, options = {}) {
  const { allowFailure = false, ...spawnOptions } = options;
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: "inherit",
    ...spawnOptions,
  });
  if (!allowFailure && result.status !== 0) {
    process.exit(result.status ?? 1);
  }
  return result;
}

function runWithInput(command, args, input, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    input,
    encoding: "utf8",
    stdio: ["pipe", "inherit", "inherit"],
    ...options,
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

function runCapture(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "inherit"],
    ...options,
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
  return result.stdout;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
