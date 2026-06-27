import crypto from "node:crypto";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");

const defaults = {
  source: "data/subtlex-pl/subtlex-pl-lemmas-master.csv",
  out: "target/local-polish-lexicon-bundle",
  limit: "25000",
};

const args = parseArgs(process.argv.slice(2));
const sourcePath = resolveFromRoot(args.source ?? defaults.source);
const outDir = resolveFromRoot(args.out ?? defaults.out);
const limit = Number.parseInt(args.limit ?? defaults.limit, 10);

if (!Number.isInteger(limit) || limit <= 0) {
  fail(`Invalid --limit: ${args.limit}`);
}

const allowedPos = new Set(["subst", "adj", "verb", "adv", "qub", "conj", "num", "prep", "pred", "pron"]);
const collator = new Intl.Collator("pl");
const nullValue = "\\N";

const tableHeaders = {
  lemmas: [
    "lemma_id",
    "lemma",
    "total_frequency_sn_sum",
    "total_frequency_sn_sum_rank",
    "total_subtlex_frequency",
    "lemma_subtlex_pos_count",
    "surface_form_count",
    "nkjp_frequency",
    "nkjp_frequency_rank",
  ],
  lemma_subtlex_pos: [
    "lemma_subtlex_pos_id",
    "lemma_id",
    "lemma",
    "subtlex_pos",
    "subtlex_frequency",
    "contextual_diversity_count",
    "contextual_diversity",
  ],
  surface_forms: ["surface_form_id", "surface_form"],
  surface_subtlex_metrics: [
    "surface_form_id",
    "spellcheck",
    "alphabetical",
    "nchar",
    "subtlex_frequency",
    "subtlex_frequency_rank",
    "capitalized_frequency",
    "contextual_diversity",
    "contextual_diversity_count",
    "dominant_pos",
    "dominant_pos_frequency",
    "dominant_lemma_pos",
    "dominant_lemma_pos_frequency",
    "dominant_lemma_pos_total_frequency",
    "all_pos",
    "all_pos_frequency",
    "all_lemma_pos",
    "all_lemma_pos_frequency",
    "all_lemma_pos_total_frequency",
    "lg_frequency",
    "lg_million_frequency",
    "zipf_frequency",
    "lg_contextual_diversity",
    "frequency_sn_sum",
    "zipf_frequency_sn_sum",
    "avg_zipf_freq_sn",
    "avg_zipf_freq_sn_rank",
  ],
  surface_nkjp_metrics: [
    "surface_form_id",
    "nkjp_frequency",
    "nkjp_frequency_rank",
    "occurrence_pct",
    "per_million",
  ],
  surface_form_lemma_links: [
    "surface_form_lemma_link_id",
    "surface_form_id",
    "lemma_id",
    "lemma_subtlex_pos_id",
  ],
  surface_form_lemma_frequency_ranks: [
    "surface_form_lemma_frequency_rank_id",
    "surface_form_id",
    "lemma_id",
    "surface_form",
    "lemma",
    "surface_frequency_sn_sum",
    "surface_lemma_link_subtlex_pos",
    "surface_lemma_link_frequency",
    "surface_lemma_link_total_frequency",
    "surface_lemma_link_frequency_share",
    "surface_lemma_link_frequency_sn_sum",
    "surface_avg_zipf_freq_sn",
    "surface_avg_zipf_freq_sn_rank",
    "lemma_total_frequency_sn_sum",
    "lemma_total_frequency_sn_sum_rank",
  ],
  freedict_entries: ["freedict_entry_id", "lemma_id", "source_entry_index", "headword", "entry_key"],
  freedict_entry_pos: ["freedict_entry_pos_id", "freedict_entry_id", "pos"],
  freedict_pronunciations: ["freedict_pronunciation_id", "freedict_entry_id", "pronunciation"],
  freedict_senses: ["freedict_sense_id", "freedict_entry_id", "sense_index"],
  freedict_sense_translations: ["freedict_sense_translation_id", "freedict_sense_id", "translation"],
  freedict_sense_definitions: ["freedict_sense_definition_id", "freedict_sense_id", "definition"],
  example_sentences: [
    "example_sentence_id",
    "sentence",
    "sentence_translation",
    "surface_form_id",
    "lemma_id",
    "lemma_subtlex_pos_id",
    "word_translation",
  ],
  lemma_pos_distractors: [
    "lemma_pos_distractor_id",
    "lemma_subtlex_pos_id",
    "distractor_translation",
    "is_default",
    "import_order",
  ],
  example_sentence_distractor_assignments: [
    "example_sentence_id",
    "lemma_pos_distractor_id",
  ],
  rejected_lemmas: [
    "rejected_lemma_id",
    "lemma",
    "subtlex_pos",
    "spelling",
    "reason_code",
    "subtlex_frequency",
    "contextual_diversity_count",
    "contextual_diversity",
    "total_frequency_sn_sum",
  ],
  rejected_surfaces: [
    "rejected_surface_id",
    "surface_form",
    "reason_code",
    "subtlex_frequency",
    "capitalized_frequency",
    "avg_zipf_freq_sn",
  ],
  rejected_freedict_rows: [
    "rejected_freedict_id",
    "lemma",
    "freedict_headword",
    "entry_key",
    "source_entry_index",
    "source_pos",
    "sense_index",
    "reason_code",
    "translation",
    "definition",
  ],
  nkjp_build_stats: ["stage", "rows", "count"],
};

const sourceRows = readSubtlexRows(sourcePath);
const topEntries = [...sourceRows.values()]
  .sort((a, b) => b.totalFrequency - a.totalFrequency || collator.compare(a.lemma, b.lemma))
  .slice(0, limit)
  .map((entry, index) => {
    const posRows = [...entry.pos.values()].sort((a, b) => b.frequency - a.frequency || a.pos.localeCompare(b.pos));
    return { ...entry, lemmaId: index + 1, rank: index + 1, posRows };
  });

if (topEntries.length === 0) {
  fail(`No importable rows found in ${sourcePath}`);
}

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(path.join(outDir, "tsv"), { recursive: true });

const tables = [];
let lemmaSubtlexPosId = 1;
let surfaceFormLemmaLinkId = 1;

const lemmas = [];
const lemmaSubtlexPos = [];
const surfaceForms = [];
const surfaceSubtlexMetrics = [];
const surfaceFormLemmaLinks = [];
const surfaceFormLemmaFrequencyRanks = [];

for (const entry of topEntries) {
  const dominant = entry.posRows[0];
  const dominantShare = dominant.frequency / entry.totalFrequency;
  const avgCd = entry.cdWeighted / entry.totalFrequency;
  const lgFrequency = log10(entry.totalFrequency);
  const lgContextualDiversity = log10(Math.max(1, entry.contextualDiversityCount));
  const allPos = entry.posRows.map((row) => row.pos).join("|");
  const allPosFrequency = entry.posRows.map((row) => `${row.pos}:${row.frequency}`).join("|");
  const allLemmaPos = entry.posRows.map((row) => `${entry.lemma}:${row.pos}`).join("|");
  const allLemmaPosFrequency = entry.posRows.map((row) => `${entry.lemma}:${row.pos}:${row.frequency}`).join("|");
  const allLemmaPosTotalFrequency = entry.posRows.map((row) => `${entry.lemma}:${row.pos}:${entry.totalFrequency}`).join("|");

  lemmas.push([
    entry.lemmaId,
    entry.lemma,
    decimal(entry.totalFrequency),
    entry.rank,
    entry.totalFrequency,
    entry.posRows.length,
    1,
    null,
    null,
  ]);

  surfaceForms.push([entry.lemmaId, entry.lemma]);

  surfaceSubtlexMetrics.push([
    entry.lemmaId,
    1,
    1,
    [...entry.lemma].length,
    entry.totalFrequency,
    entry.rank,
    0,
    decimal(avgCd),
    entry.contextualDiversityCount,
    dominant.pos,
    dominant.frequency,
    `${entry.lemma}:${dominant.pos}`,
    dominant.frequency,
    entry.totalFrequency,
    allPos,
    allPosFrequency,
    allLemmaPos,
    allLemmaPosFrequency,
    allLemmaPosTotalFrequency,
    decimal(lgFrequency),
    null,
    decimal(lgFrequency),
    decimal(lgContextualDiversity),
    entry.totalFrequency,
    decimal(lgFrequency),
    decimal(lgFrequency),
    entry.rank,
  ]);

  surfaceFormLemmaFrequencyRanks.push([
    entry.rank,
    entry.lemmaId,
    entry.lemmaId,
    entry.lemma,
    entry.lemma,
    entry.totalFrequency,
    dominant.pos,
    dominant.frequency,
    entry.totalFrequency,
    decimal(dominantShare),
    decimal(dominant.frequency),
    decimal(lgFrequency),
    entry.rank,
    decimal(entry.totalFrequency),
    entry.rank,
  ]);

  for (const posRow of entry.posRows) {
    const currentLemmaSubtlexPosId = lemmaSubtlexPosId++;
    lemmaSubtlexPos.push([
      currentLemmaSubtlexPosId,
      entry.lemmaId,
      entry.lemma,
      posRow.pos,
      posRow.frequency,
      posRow.contextualDiversityCount,
      decimal(posRow.cdWeighted / posRow.frequency),
    ]);
    surfaceFormLemmaLinks.push([
      surfaceFormLemmaLinkId++,
      entry.lemmaId,
      entry.lemmaId,
      currentLemmaSubtlexPosId,
    ]);
  }
}

writeTable("lemmas", lemmas);
writeTable("lemma_subtlex_pos", lemmaSubtlexPos);
writeTable("surface_forms", surfaceForms);
writeTable("surface_subtlex_metrics", surfaceSubtlexMetrics);
writeTable("surface_nkjp_metrics", []);
writeTable("surface_form_lemma_links", surfaceFormLemmaLinks);
writeTable("surface_form_lemma_frequency_ranks", surfaceFormLemmaFrequencyRanks);
writeTable("freedict_entries", []);
writeTable("freedict_entry_pos", []);
writeTable("freedict_pronunciations", []);
writeTable("freedict_senses", []);
writeTable("freedict_sense_translations", []);
writeTable("freedict_sense_definitions", []);
writeTable("lemma_pos_distractors", []);
writeTable("example_sentences", []);
writeTable("example_sentence_distractor_assignments", []);
writeTable("rejected_lemmas", []);
writeTable("rejected_surfaces", []);
writeTable("rejected_freedict_rows", []);
writeTable("nkjp_build_stats", [["subtlex_importable_lemmas", sourceRows.size, topEntries.length]]);

const manifest = {
  schema_name: "polish_lexicon",
  bundle_name: `local-subtlex-top-${topEntries.length}`,
  selection_scope: `top ${topEntries.length} lowercase SUBTLEX lemmas with supported POS`,
  source_git_commit_sha: gitCommit(),
  generated_at: new Date().toISOString(),
  encoding: "utf-8",
  delimiter: "\t",
  header: true,
  null_value: nullValue,
  tables,
};

const manifestPath = path.join(outDir, "manifest.json");
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

console.log(`Built ${path.relative(root, outDir)}`);
console.log(`lemmas ${topEntries.length}; lemma_pos ${lemmaSubtlexPos.length}`);

function readSubtlexRows(file) {
  if (!fs.existsSync(file)) {
    fail(`Missing SUBTLEX source: ${file}`);
  }

  const lines = fs.readFileSync(file, "utf8").trimEnd().split(/\r?\n/);
  const header = lines.shift();
  if (header !== "lemma\tpos\tspelling\tfreq\tcd.count\tcd") {
    fail(`Unexpected SUBTLEX header: ${header}`);
  }

  const entries = new Map();
  for (const line of lines) {
    const [lemma, pos, , frequencyRaw, contextualDiversityCountRaw, contextualDiversityRaw] = line.split("\t");
    if (!allowedPos.has(pos) || !isImportableLemma(lemma)) continue;

    const frequency = Number.parseInt(frequencyRaw, 10);
    const contextualDiversityCount = Number.parseInt(contextualDiversityCountRaw, 10);
    const contextualDiversity = Number.parseFloat(contextualDiversityRaw);
    if (!Number.isFinite(frequency) || frequency <= 0) continue;
    if (!Number.isFinite(contextualDiversityCount) || !Number.isFinite(contextualDiversity)) continue;

    let entry = entries.get(lemma);
    if (!entry) {
      entry = {
        lemma,
        totalFrequency: 0,
        contextualDiversityCount: 0,
        cdWeighted: 0,
        pos: new Map(),
      };
      entries.set(lemma, entry);
    }

    let posEntry = entry.pos.get(pos);
    if (!posEntry) {
      posEntry = {
        pos,
        frequency: 0,
        contextualDiversityCount: 0,
        cdWeighted: 0,
      };
      entry.pos.set(pos, posEntry);
    }

    entry.totalFrequency += frequency;
    entry.contextualDiversityCount += contextualDiversityCount;
    entry.cdWeighted += contextualDiversity * frequency;
    posEntry.frequency += frequency;
    posEntry.contextualDiversityCount += contextualDiversityCount;
    posEntry.cdWeighted += contextualDiversity * frequency;
  }

  return entries;
}

function writeTable(table, rows) {
  const header = tableHeaders[table];
  if (!header) fail(`Missing table header for ${table}`);

  const relativeFile = `tsv/${table}.tsv`;
  const file = path.join(outDir, relativeFile);
  const content = [header.join("\t"), ...rows.map((row) => row.map(tsvCell).join("\t"))].join("\n") + "\n";
  fs.writeFileSync(file, content, "utf8");
  tables.push({
    table,
    file: relativeFile,
    rows: rows.length,
    sha256: sha256(file),
    header,
  });
}

function tsvCell(value) {
  if (value === null || value === undefined) return nullValue;
  const stringValue = String(value);
  if (/["\r\n\t]/.test(stringValue)) {
    return `"${stringValue.replaceAll('"', '""')}"`;
  }
  return stringValue;
}

function decimal(value, places = 6) {
  if (!Number.isFinite(value)) return null;
  return value.toFixed(places).replace(/\.?0+$/, "");
}

function log10(value) {
  return Math.log10(Math.max(1, value));
}

function isImportableLemma(value) {
  return /^\p{Letter}+$/u.test(value) && value === value.toLocaleLowerCase("pl-PL");
}

function sha256(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function gitCommit() {
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  } catch {
    return "unknown";
  }
}

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith("--")) fail(`Unknown argument: ${arg}`);
    const key = arg.slice(2);
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) fail(`Missing value for ${arg}`);
    parsed[key] = value;
    index += 1;
  }
  return parsed;
}

function resolveFromRoot(value) {
  return path.isAbsolute(value) ? value : path.resolve(root, value);
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
