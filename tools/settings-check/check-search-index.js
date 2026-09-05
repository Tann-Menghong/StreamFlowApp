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

const SETTINGS = 'app/src/main/java/com/streamflow/ui/settings/SettingsScreen.kt';
const INDEX = 'app/src/main/java/com/streamflow/data/SettingsIndex.kt';

const src = fs.readFileSync(SETTINGS, 'utf8').split('\n');
const idx = fs.readFileSync(INDEX, 'utf8').split('\n');

// ── what the UI actually renders, per category ──────────────────────────────
const whenLine = src.findIndex((l) => /when \(category\)/.test(l));
if (whenLine < 0) {
  console.log('FAIL - could not find `when (category)` in SettingsScreen.kt');
  process.exit(2);
}
const branches = [];
let whenEnd = src.length;
for (let i = whenLine + 1; i < src.length; i++) {
  if (/^ {16}else ->/.test(src[i])) { whenEnd = i; break; }
  const m = src[i].match(/^ {16}"([^"]+)" ->/);
  if (m) branches.push([m[1], i]);
}
const rows = new Map(); // category -> Set(label)
for (let b = 0; b < branches.length; b++) {
  const [name, start] = branches[b];
  const stop = b + 1 < branches.length ? branches[b + 1][1] : whenEnd;
  const set = new Set();
  for (let i = start; i < stop; i++) {
    const m = src[i].match(/Settings(?:Item|SwitchItem)\(\s*[^,]+,\s*"([^"]+)"/);
    if (m) set.add(m[1]);
  }
  rows.set(name, set);
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

console.log(`  ${claims.length} indexed settings across ${byCategory.size} pages\n`);
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
