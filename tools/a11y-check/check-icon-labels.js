// Flags icon-only controls that a screen reader cannot announce.
//
// A null contentDescription is CORRECT for a decorative glyph sitting beside a
// text label — that is why a blanket "label everything" pass would make TalkBack
// noisier, not better. It is wrong for an IconButton, where the icon IS the
// control: TalkBack announces "button", unlabelled, and the user has no way to
// know what it does.
//
// The player is the worst case, being almost entirely icon-only controls.
const fs = require('fs');
const path = require('path');

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}

const ROOT = 'app/src/main/java/com/streamflow';
let unlabelled = 0;
let labelled = 0;
const perFile = {};

for (const file of walk(ROOT)) {
  const src = fs.readFileSync(file, 'utf8').split('\n');
  for (let i = 0; i < src.length; i++) {
    if (!/IconButton\s*\(/.test(src[i])) continue;
    // Scan the IconButton body by brace depth and inspect the Icon( call inside.
    let depth = 0, j = i, started = false;
    while (j < src.length && j < i + 40) {
      depth += (src[j].match(/\(/g) || []).length - (src[j].match(/\)/g) || []).length;
      if (src[j].includes('(')) started = true;
      const m = src[j].match(/Icon\(\s*([^,]+),\s*([^,)]+)/);
      if (m && j >= i) {
        const desc = m[2].trim();
        if (desc === 'null') {
          unlabelled++;
          (perFile[file] = perFile[file] || []).push(j + 1);
        } else {
          labelled++;
        }
        break;
      }
      if (started && depth <= 0 && j > i) break;
      j++;
    }
  }
}

const files = Object.keys(perFile).sort(
  (a, b) => perFile[b].length - perFile[a].length
);
for (const f of files) {
  console.log(`${perFile[f].length.toString().padStart(3)}  ${f}`);
  console.log(`     lines: ${perFile[f].join(', ')}`);
}

console.log('');
console.log(`labelled icon buttons   : ${labelled}`);
console.log(`unlabelled icon buttons : ${unlabelled}`);
console.log('');
console.log(
  unlabelled === 0
    ? 'PASS - every icon-only button has a screen-reader label'
    : `FAIL - ${unlabelled} icon-only button(s) are unreachable with TalkBack`
);
process.exit(unlabelled === 0 ? 0 : 1);
