// Every Settings category must be reachable, labelled, and owned by exactly
// one page.
//
// The Settings screen is a dashboard of tiles that navigate by NAME: tapping a
// tile routes to settings/{name}, and SettingsCategoryScreen switches on that
// string. Three separate places have to agree, none of them checked by the
// compiler:
//
//   settingsSections   the tile exists and is tappable
//   tileSubtitles      the tile has a subtitle under its name
//   when (category)    the page has content
//
// A tile with no branch falls through to `else -> {}` and opens a page that is
// completely blank, with a working back button and nothing else. A branch with
// no tile is a page nothing can reach. Both compile, and neither shows up in
// lint.
//
// The fourth check is the one that prompted this file. Settings had grown so
// that the app lock lived at the bottom of a group called "Data & privacy" on
// the *Playback* page, the Wi-Fi-only download switch sat beside it, and the
// automatic downloader was on the *Storage* page. Nothing was duplicated yet,
// but nothing stopped it from being: the same control can be added to a second
// page and the two copies will write the same preference and disagree on
// screen until one of them is noticed. A row label may appear on one page only.
const fs = require('fs');
const { extractRows, categoryBranches } = require('./rows.js');

const FILE = 'app/src/main/java/com/streamflow/ui/settings/SettingsScreen.kt';
const src = fs.readFileSync(FILE, 'utf8').split('\n');

let fail = 0;
const problem = (msg) => { console.log(`  ${msg}`); fail = 1; };

// ── the three lists that must agree ─────────────────────────────────────────
const tiles = [];
const subtitles = new Set();
for (let i = 0; i < src.length; i++) {
  const t = src[i].match(/SettingsTile\("([^"]+)"/);
  if (t) tiles.push({ name: t[1], line: i + 1 });
  const s = src[i].match(/^\s{8}"([^"]+)" to /);
  if (s) subtitles.add(s[1]);
}

// Category branches, via the shared extractor so this and check-search-index.js
// cannot disagree about what a row is.
const parsed = categoryBranches(src);
if (!parsed) {
  console.log('FAIL - could not find `when (category)`; has SettingsCategoryScreen been restructured?');
  process.exit(2);
}
const branches = new Map([...parsed.branches].map(([n, r]) => [n, r.start]));
const whenEnd = parsed.whenEnd;

console.log(`  ${tiles.length} tiles, ${branches.size} category pages`);
console.log('');
console.log('  CATEGORY          TILE  SUBTITLE  PAGE');
console.log('  ' + '-'.repeat(44));
for (const t of tiles) {
  const hasSub = subtitles.has(t.name);
  const hasPage = branches.has(t.name);
  console.log(
    `  ${t.name.padEnd(17)} ${'yes'.padEnd(5)} ${(hasSub ? 'yes' : 'MISSING').padEnd(9)} ${hasPage ? 'yes' : 'MISSING'}`
  );
  if (!hasSub) problem(`${FILE}:${t.line}  tile "${t.name}" has no tileSubtitles entry — its row shows a bare name`);
  if (!hasPage) problem(`${FILE}:${t.line}  tile "${t.name}" has no when(category) branch — it opens a blank page`);
}
for (const [name, line] of branches) {
  if (!tiles.some((t) => t.name === name)) {
    console.log(`  ${name.padEnd(17)} ${'MISSING'.padEnd(5)} ${'-'.padEnd(9)} yes`);
    problem(`${FILE}:${line + 1}  page "${name}" has no tile — nothing navigates to it`);
  }
}

// ── no control may live on two pages ────────────────────────────────────────
const ordered = [...branches.entries()].sort((a, b) => a[1] - b[1]);
const owner = new Map(); // label -> {category, line}
console.log('');
for (let b = 0; b < ordered.length; b++) {
  const [name, start] = ordered[b];
  const end = b + 1 < ordered.length ? ordered[b + 1][1] : whenEnd;
  for (const { label, line } of extractRows(src.slice(start, end), start + 1)) {
    const prev = owner.get(label);
    if (prev && prev.category !== name) {
      problem(
        `${FILE}:${line}  "${label}" is on both the ${prev.category} page (line ${prev.line}) ` +
        `and the ${name} page — one control, two homes`
      );
    } else if (!prev) {
      owner.set(label, { category: name, line });
    }
  }
}

console.log('');
console.log(
  fail === 0
    ? 'PASS - every category is reachable, labelled, and owns its rows'
    : 'FAIL - see above'
);
process.exit(fail);
