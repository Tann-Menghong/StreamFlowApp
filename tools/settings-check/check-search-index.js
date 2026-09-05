// Settings search must never point at a setting that is not there.
//
// SettingsIndex is a hand-written list of "this control lives on that page".
// Nothing in Kotlin ties it to the actual UI: renaming a row, moving it to
// another category, or deleting it leaves the index compiling perfectly and
// quietly wrong. The failure is worse than a missing feature -- search would
// confidently navigate the user to a page and leave them hunting for a control
// that no longer exists there, which reads as the app being broken.
//
// So every entry is verified against the rows SettingsScreen.kt actually
// renders, inside the branch the entry claims. The reverse is deliberately NOT
// required: plenty of rows are conditional state rather than settings
// ("Downloading AI model…", "Tab limit reached", "StreamFlow crashed last
// time"), and indexing those would be worse than leaving them out.
const fs = require('fs');
const { extractRows, categoryBranches } = require('./rows.js');

const SETTINGS = 'app/src/main/java/com/streamflow/ui/settings/SettingsScreen.kt';
const INDEX = 'app/src/main/java/com/streamflow/data/SettingsIndex.kt';

const src = fs.readFileSync(SETTINGS, 'utf8').split('\n');
const idx = fs.readFileSync(INDEX, 'utf8').split('\n');

// ── what the UI actually renders, per category ──────────────────────────────
const parsed = categoryBranches(src);
if (!parsed) {
  console.log('FAIL - could not find `when (category)` in SettingsScreen.kt');
  process.exit(2);
}
const rows = new Map(); // category -> Set(label)
for (const [name, { start, stop }] of parsed.branches) {
  rows.set(
    name,
    new Set(extractRows(src.slice(start, stop), start + 1).map((r) => r.label))
  );
}

// ── what the index claims ───────────────────────────────────────────────────
const claims = [];
for (let i = 0; i < idx.length; i++) {
  const m = idx[i].match(/^\s*Entry\("((?:[^"\\]|\\.)*)",\s*"([^"]+)"/);
  if (m) claims.push({ title: m[1], category: m[2], line: i + 1 });
}
if (claims.length === 0) {
  console.log('FAIL - parsed no entries out of SettingsIndex.kt; has its shape changed?');
  process.exit(2);
}

let fail = 0;
const byCategory = new Map();
for (const c of claims) byCategory.set(c.category, (byCategory.get(c.category) || 0) + 1);

console.log(`  ${claims.length} indexed settings across ${byCategory.size} pages`);
console.log('');
for (const [cat, n] of [...byCategory].sort()) {
  const total = rows.has(cat) ? rows.get(cat).size : 0;
  console.log(`    ${cat.padEnd(15)} ${String(n).padStart(2)} indexed / ${total} rows on the page`);
}
console.log('');

for (const c of claims) {
  if (!rows.has(c.category)) {
    console.log(`  ${INDEX}:${c.line}  "${c.title}" claims page "${c.category}", which has no when(category) branch`);
    fail = 1;
    continue;
  }
  if (!rows.get(c.category).has(c.title)) {
    // Point at where it actually is, when it is somewhere.
    const elsewhere = [...rows.entries()].find(([, set]) => set.has(c.title));
    const hint = elsewhere
      ? ` — it is on the ${elsewhere[0]} page now`
      : ' — no row with that label exists anywhere in Settings';
    console.log(`  ${INDEX}:${c.line}  "${c.title}" is not on the ${c.category} page${hint}`);
    fail = 1;
  }
}

// Duplicate titles would make one of the two results unreachable in the list.
const seen = new Set();
for (const c of claims) {
  const key = c.title.toLowerCase();
  if (seen.has(key)) {
    console.log(`  ${INDEX}:${c.line}  "${c.title}" is indexed twice`);
    fail = 1;
  }
  seen.add(key);
}

console.log(
  fail === 0
    ? 'PASS - every indexed setting exists on the page the index names'
    : 'FAIL - search would navigate to a setting that is not there'
);
process.exit(fail);
