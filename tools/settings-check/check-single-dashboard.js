// The Library screen may render exactly one dashboard.
//
// It shipped with two. LibraryScreen drew a DashboardPane of tiles above the
// tab strip, and the History tab passed a second DashboardPane into its list
// as a header — so opening Library on History (a tab any user can set as their
// default in Settings) showed two stacked bordered panes of statistics. They
// were not even consistent: one counted "this week" as the seven calendar days
// its bar chart drew, the other as the last 168 hours, and both were on screen
// at once under labels that claimed to mean the same thing.
//
// The fix removed the second surface rather than hiding it, and that is what
// this checks: hiding a duplicate leaves the code that produced it in place to
// be re-enabled by the next person who wants stats on a tab. One pane, inside
// the one composable whose job is the dashboard.
//
// Deliberately scoped to this file. Settings renders several panes, but each
// one belongs to a different category page and no two are ever on screen
// together, so a blanket codebase rule would be wrong.
const fs = require('fs');

const FILE = 'app/src/main/java/com/streamflow/ui/library/LibraryScreen.kt';
const OWNER = 'LibraryDashboard';
const src = fs.readFileSync(FILE, 'utf8').split('\n');

// The composable each line sits in, by walking forward from the last top-level
// `fun` declaration seen.
let current = '<file scope>';
const panes = [];
for (let i = 0; i < src.length; i++) {
  const fn = src[i].match(/^(?:private )?fun ([A-Za-z_]\w*)\s*\(/);
  if (fn) current = fn[1];
  // `[({]` because every parameter but the content lambda has a default, so
  // `DashboardPane { ... }` with no argument list is a legal second pane and an
  // easy one to add. Matching only `DashboardPane(` missed it.
  if (/DashboardPane\s*[({]/.test(src[i])) panes.push({ fn: current, line: i + 1 });
}

console.log(`  dashboard panes in ${FILE.split('/').pop()}: ${panes.length}`);
for (const p of panes) console.log(`    line ${String(p.line).padStart(4)}  in ${p.fn}()`);
console.log('');

let fail = 0;
if (panes.length === 0) {
  console.log(`  the Library dashboard is gone entirely — expected one pane in ${OWNER}()`);
  fail = 1;
} else if (panes.length > 1) {
  console.log(`  ${panes.length} dashboards on one screen; the Library shows ${panes.length > 2 ? 'several' : 'two'} stacked panes of statistics`);
  fail = 1;
}
for (const p of panes) {
  if (p.fn !== OWNER) {
    console.log(`  line ${p.line}: a dashboard pane outside ${OWNER}() (in ${p.fn}) — the dashboard has a second source`);
    fail = 1;
  }
}

console.log('');
console.log(
  fail === 0
    ? `PASS - one Library dashboard, owned by ${OWNER}()`
    : 'FAIL - see above'
);
process.exit(fail);
