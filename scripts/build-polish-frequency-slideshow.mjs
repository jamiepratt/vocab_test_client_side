import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const sourcePath = path.join(root, "data/subtlex-pl/subtlex-pl-lemmas-master.csv");
const limit = 25000;
const perSlide = 50;
const tsvPath = path.join(root, `data/subtlex-pl/top-${limit}-polish-lemmas.tsv`);
const htmlPath = path.join(root, "public/polish-frequency-slideshow.html");

const allowedPos = new Set(["subst", "adj", "verb", "adv", "qub", "conj", "num", "prep", "pred", "pron"]);
const lemmaPattern = /^[a-ząćęłńóśźż]+$/u;
const rows = fs.readFileSync(sourcePath, "utf8").trimEnd().split(/\r?\n/);
const entries = new Map();

for (const row of rows.slice(1)) {
  const [lemma, pos, , freqRaw] = row.split("\t");
  if (!allowedPos.has(pos) || !lemmaPattern.test(lemma)) continue;

  const freq = Number(freqRaw);
  if (!Number.isFinite(freq)) continue;

  const existing = entries.get(lemma) ?? { lemma, freq: 0, posFreq: new Map() };
  existing.freq += freq;
  existing.posFreq.set(pos, (existing.posFreq.get(pos) ?? 0) + freq);
  entries.set(lemma, existing);
}

const top = [...entries.values()]
  .map((entry) => {
    const pos = [...entry.posFreq.entries()].sort((a, b) => b[1] - a[1])[0][0];
    return { lemma: entry.lemma, freq: entry.freq, pos };
  })
  .sort((a, b) => b.freq - a.freq || a.lemma.localeCompare(b.lemma, "pl"))
  .slice(0, limit)
  .map((entry, index) => ({ rank: index + 1, ...entry }));

fs.writeFileSync(
  tsvPath,
  ["rank\tlemma\tfreq\tdominant_pos", ...top.map((w) => `${w.rank}\t${w.lemma}\t${w.freq}\t${w.pos}`)].join("\n") + "\n",
);

const wordsJson = JSON.stringify(top);

const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Top ${limit} Polish Lemmas</title>
  <style>
    :root {
      color-scheme: light;
      --ink: #17212b;
      --muted: #5d6873;
      --line: #d7dee5;
      --paper: #f7f9fb;
      --panel: #ffffff;
      --accent: #196f7a;
      --accent-2: #8a4b18;
      --tile: #eef5f6;
    }

    * {
      box-sizing: border-box;
    }

    body {
      margin: 0;
      min-height: 100vh;
      background: var(--paper);
      color: var(--ink);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      letter-spacing: 0;
    }

    main {
      min-height: 100vh;
      display: grid;
      grid-template-rows: auto 1fr auto;
    }

    header,
    footer {
      padding: 18px clamp(18px, 4vw, 48px);
    }

    header {
      display: flex;
      align-items: end;
      justify-content: space-between;
      gap: 16px;
      border-bottom: 1px solid var(--line);
      background: var(--panel);
    }

    h1 {
      margin: 0;
      font-size: clamp(24px, 3vw, 42px);
      line-height: 1.05;
      font-weight: 780;
    }

    .meta {
      color: var(--muted);
      font-size: 14px;
      line-height: 1.45;
    }

    .stage {
      display: grid;
      align-content: center;
      padding: clamp(18px, 4vw, 48px);
    }

    .slide {
      width: min(1180px, 100%);
      margin: 0 auto;
    }

    .slide-title {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 16px;
    }

    .slide-title h2 {
      margin: 0;
      font-size: clamp(18px, 2vw, 28px);
      line-height: 1.15;
    }

    .counter {
      color: var(--accent-2);
      font-weight: 700;
      white-space: nowrap;
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      gap: 10px;
    }

    .word {
      min-height: 58px;
      display: grid;
      grid-template-columns: 42px minmax(0, 1fr);
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      box-shadow: 0 1px 0 rgba(23, 33, 43, 0.03);
    }

    .rank {
      color: var(--accent);
      font-size: 13px;
      font-weight: 760;
      font-variant-numeric: tabular-nums;
    }

    .lemma {
      min-width: 0;
      overflow-wrap: anywhere;
      font-size: clamp(17px, 1.6vw, 24px);
      line-height: 1.1;
      font-weight: 720;
    }

    .freq {
      grid-column: 2;
      color: var(--muted);
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }

    footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      border-top: 1px solid var(--line);
      background: var(--panel);
    }

    .controls {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    button {
      min-width: 44px;
      min-height: 40px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--tile);
      color: var(--ink);
      font: inherit;
      font-weight: 760;
      cursor: pointer;
    }

    button:hover {
      border-color: var(--accent);
    }

    button:disabled {
      cursor: default;
      opacity: 0.45;
    }

    .jump {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--muted);
      font-size: 14px;
      white-space: nowrap;
    }

    .jump input {
      width: 76px;
      min-height: 40px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      color: var(--ink);
      font: inherit;
      font-weight: 720;
      text-align: center;
    }

    .progress {
      flex: 1;
      height: 8px;
      border-radius: 999px;
      background: #e6ebef;
      overflow: hidden;
    }

    .bar {
      height: 100%;
      width: 0;
      background: linear-gradient(90deg, var(--accent), var(--accent-2));
    }

    @media (max-width: 900px) {
      .grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }

    @media (max-width: 620px) {
      header,
      footer {
        align-items: start;
        flex-direction: column;
      }

      .grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .word {
        grid-template-columns: 36px minmax(0, 1fr);
      }
    }
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>Top ${limit} Polish Lemmas</h1>
        <div class="meta">SUBTLEX-PL, subtitle-based frequencies. Lowercase alphabetic lemmas, duplicate POS rows aggregated.</div>
      </div>
      <div class="meta" id="slideMeta"></div>
    </header>

    <section class="stage" aria-live="polite">
      <div class="slide">
        <div class="slide-title">
          <h2 id="rangeTitle"></h2>
          <div class="counter" id="counter"></div>
        </div>
        <div class="grid" id="grid"></div>
      </div>
    </section>

    <footer>
      <div class="controls">
        <button type="button" id="first" aria-label="First slide">&lt;&lt;</button>
        <button type="button" id="prev" aria-label="Previous slide">&lt;</button>
        <label class="jump">Slide <input id="jump" type="number" min="1" inputmode="numeric"></label>
        <button type="button" id="next" aria-label="Next slide">&gt;</button>
        <button type="button" id="last" aria-label="Last slide">&gt;&gt;</button>
      </div>
      <div class="progress" aria-hidden="true"><div class="bar" id="bar"></div></div>
    </footer>
  </main>

  <script>
    const words = ${wordsJson};
    const perSlide = ${perSlide};
    const totalSlides = Math.ceil(words.length / perSlide);
    let slide = 0;

    const grid = document.getElementById("grid");
    const rangeTitle = document.getElementById("rangeTitle");
    const counter = document.getElementById("counter");
    const slideMeta = document.getElementById("slideMeta");
    const bar = document.getElementById("bar");
    const first = document.getElementById("first");
    const prev = document.getElementById("prev");
    const next = document.getElementById("next");
    const last = document.getElementById("last");
    const jump = document.getElementById("jump");

    jump.max = totalSlides;

    function formatNumber(value) {
      return new Intl.NumberFormat("en-US").format(value);
    }

    function render() {
      const start = slide * perSlide;
      const page = words.slice(start, start + perSlide);
      const firstRank = page[0].rank;
      const lastRank = page[page.length - 1].rank;

      rangeTitle.textContent = "Ranks " + firstRank + "-" + lastRank;
      counter.textContent = "Slide " + (slide + 1) + " / " + totalSlides;
      slideMeta.textContent = formatNumber(words.length) + " words";
      bar.style.width = ((slide + 1) / totalSlides * 100) + "%";
      jump.value = slide + 1;
      first.disabled = slide === 0;
      prev.disabled = slide === 0;
      next.disabled = slide === totalSlides - 1;
      last.disabled = slide === totalSlides - 1;

      grid.replaceChildren(...page.map((item) => {
        const card = document.createElement("article");
        card.className = "word";

        const rank = document.createElement("div");
        rank.className = "rank";
        rank.textContent = "#" + item.rank;

        const lemma = document.createElement("div");
        lemma.className = "lemma";
        lemma.textContent = item.lemma;

        const freq = document.createElement("div");
        freq.className = "freq";
        freq.textContent = formatNumber(item.freq) + " · " + item.pos;

        card.append(rank, lemma, freq);
        return card;
      }));
    }

    prev.addEventListener("click", () => {
      slide = Math.max(0, slide - 1);
      render();
    });

    next.addEventListener("click", () => {
      slide = Math.min(totalSlides - 1, slide + 1);
      render();
    });

    first.addEventListener("click", () => {
      slide = 0;
      render();
    });

    last.addEventListener("click", () => {
      slide = totalSlides - 1;
      render();
    });

    jump.addEventListener("change", () => {
      const value = Number(jump.value);
      if (!Number.isFinite(value)) return render();
      slide = Math.min(totalSlides - 1, Math.max(0, Math.round(value) - 1));
      render();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "ArrowLeft") prev.click();
      if (event.key === "ArrowRight" || event.key === " ") {
        event.preventDefault();
        next.click();
      }
      if (event.key === "Home") first.click();
      if (event.key === "End") last.click();
    });

    render();
  </script>
</body>
</html>
`;

fs.writeFileSync(htmlPath, html);

console.log(`Wrote ${tsvPath}`);
console.log(`Wrote ${htmlPath}`);
