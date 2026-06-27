import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadDotEnv } from "./load-env.mjs";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");

loadDotEnv();

const databaseUrl = process.env.DATABASE_URL || "postgresql://localhost:5432/vocab_test_client_side";
const bundleDir = process.env.LOCAL_DB_BUNDLE_DIR || "target/local-polish-lexicon-bundle";
const db = parseDatabaseUrl(databaseUrl);

const pgEnv = { ...process.env };
if (db.password) pgEnv.PGPASSWORD = db.password;

const psqlBaseArgs = ["-h", db.host, "-p", db.port];
if (db.user) psqlBaseArgs.push("-U", db.user);

const exists = runCapture("psql", [
  ...psqlBaseArgs,
  "-d",
  "postgres",
  "-Atc",
  `select 1 from pg_database where datname = '${db.name.replaceAll("'", "''")}'`,
], { env: pgEnv }).trim();

if (exists === "1") {
  console.log(`Database ${db.name} already exists`);
} else {
  console.log(`Creating database ${db.name}`);
  run("createdb", [...psqlBaseArgs, db.name], { env: pgEnv });
}

run("node", ["scripts/build-local-db-bundle.mjs", "--out", bundleDir], { env: process.env });

const clojureEnv = { ...process.env, DATABASE_URL: databaseUrl };
run("clojure", ["-M:db", "migrate"], { env: clojureEnv });
run("clojure", ["-M:db", "import", bundleDir, "--replace"], { env: clojureEnv });
run("clojure", ["-M:db", "verify", bundleDir], { env: clojureEnv });

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: "inherit",
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

function parseDatabaseUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    fail("DATABASE_URL must be a postgres:// or postgresql:// URL for local setup.");
  }

  if (url.protocol !== "postgres:" && url.protocol !== "postgresql:") {
    fail("DATABASE_URL must use postgres:// or postgresql:// for local setup.");
  }

  const name = decodeURIComponent(url.pathname.replace(/^\//, ""));
  if (!name) fail("DATABASE_URL must include a database name.");

  return {
    host: url.hostname || "localhost",
    port: url.port || "5432",
    user: url.username ? decodeURIComponent(url.username) : undefined,
    password: url.password ? decodeURIComponent(url.password) : undefined,
    name,
  };
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
