import { spawnSync } from "node:child_process";
import { loadDotEnv } from "./load-env.mjs";

loadDotEnv();

const separatorIndex = process.argv.indexOf("--");
const args = separatorIndex === -1
  ? process.argv.slice(2)
  : process.argv.slice(separatorIndex + 1);

if (args.length === 0) {
  console.error("Usage: node scripts/run-with-env.mjs -- <command> [args...]");
  process.exit(1);
}

const result = spawnSync(args[0], args.slice(1), {
  stdio: "inherit",
  env: process.env,
});

process.exit(result.status ?? 1);

