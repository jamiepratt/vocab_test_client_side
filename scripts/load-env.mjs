import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");

export function loadDotEnv({ env = process.env, file = path.join(root, ".env"), override = false } = {}) {
  if (!fs.existsSync(file)) return;

  const content = fs.readFileSync(file, "utf8");
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const assignment = line.startsWith("export ") ? line.slice("export ".length).trim() : line;
    const separatorIndex = assignment.indexOf("=");
    if (separatorIndex === -1) continue;

    const key = assignment.slice(0, separatorIndex).trim();
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;
    if (!override && env[key] !== undefined) continue;

    env[key] = parseValue(assignment.slice(separatorIndex + 1).trim());
  }
}

function parseValue(value) {
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    return value
      .slice(1, -1)
      .replace(/\\n/g, "\n")
      .replace(/\\r/g, "\r")
      .replace(/\\t/g, "\t")
      .replace(/\\"/g, '"')
      .replace(/\\\\/g, "\\");
  }

  if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
    return value.slice(1, -1);
  }

  return value;
}

