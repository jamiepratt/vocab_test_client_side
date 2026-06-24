import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const outDir = process.argv[2] ?? "target/release/public";
const apiBaseUrl = process.env.VOCAB_API_BASE_URL ?? "https://vocab-test.fly.dev";

await mkdir(outDir, { recursive: true });
await writeFile(
  path.join(outDir, "config.js"),
  `window.VOCAB_CONFIG = ${JSON.stringify({ apiBaseUrl }, null, 2)};\n`,
);
