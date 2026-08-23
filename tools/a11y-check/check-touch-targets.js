#!/usr/bin/env node
// Reports IconButtons whose explicit size is below the 48dp minimum touch target.
//
// This REPORTS rather than fails. The sizes are real accessibility problems --
// an 18dp control is hard to hit deliberately and much harder with reduced
// dexterity -- but the fix is not mechanical: an IconButton forced to 48dp
// inside a compact row changes that row's layout, and layout cannot be judged
// from source. So the list is measured and tracked here, and each entry gets
// fixed when someone can see the screen it is on.
//
// Material Design 3: every touch target should be at least 48x48dp.
const fs = require('fs');
const path = require('path');

const MIN_DP = 48;
const root = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'java');

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

const findings = [];
for (const file of walk(root)) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, i) => {
    if (!line.includes('IconButton(')) return;
    // Only the IconButton's own modifier counts. An Icon(...) nested inside may
    // legitimately be 16dp -- that is the glyph, not the target.
    const window = lines.slice(i, i + 3).join(' ');
    const m = window.match(/IconButton\([^)]*?modifier\s*=\s*Modifier\.size\((\d+)\.dp\)/);
    if (!m) return;
    const dp = parseInt(m[1], 10);
    if (dp < MIN_DP) {
      findings.push({
        file: path.relative(path.join(__dirname, '..', '..'), file).split(String.fromCharCode(92)).join("/"),
        line: i + 1,
        dp,
      });
    }
  });
}

findings.sort((a, b) => a.dp - b.dp);
console.log('');
console.log('== touch targets below the 48dp minimum ==');
console.log('');
if (findings.length === 0) {
  console.log('  none');
} else {
  for (const f of findings) {
    const sev = f.dp < 24 ? 'HARD TO HIT' : 'below minimum';
    console.log(`  ${String(f.dp).padStart(2)}dp  ${sev.padEnd(13)} ${f.file}:${f.line}`);
  }
}
console.log('');
console.log(`  ${findings.length} undersized, minimum is ${MIN_DP}dp`);
console.log('');
console.log('REPORT ONLY - these are tracked, not enforced: resizing a control');
console.log('changes the layout around it, which cannot be judged from source.');
