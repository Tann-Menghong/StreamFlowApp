// Generalised version of the player check: scan EVERY Kotlin file for `when`
// blocks containing a duplicate branch condition. Duplicates are legal Kotlin,
// invisible to lint, and silently make the later branch unreachable - exactly
// the defect that blacked out video for three releases.
const fs = require('fs'), path = require('path');
function walk(d, out=[]) {
  for (const e of fs.readdirSync(d, {withFileTypes:true})) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p);
  }
  return out;
}
let findings = 0;
for (const f of walk('app/src/main/java')) {
  const src = fs.readFileSync(f, 'utf8').split('\n');
  for (let i = 0; i < src.length; i++) {
    if (!/\bwhen\s*[({]/.test(src[i])) continue;
    let depth = 0, j = i, started = false, seen = new Map();
    while (j < src.length) {
      depth += (src[j].match(/{/g)||[]).length - (src[j].match(/}/g)||[]).length;
      if (src[j].includes('{')) started = true;
      if (j > i) {
        // only branches at the when's own nesting level
        const m = src[j].match(/^\s*(is\s+[\w.]+|[A-Za-z_][\w.]*)\s*->/);
        if (m && depth === 1) {
          const k = m[1].trim();
          if (k !== 'else') {
            if (seen.has(k)) {
              console.log(`${f}:${j+1}  duplicate branch "${k}" (first at line ${seen.get(k)})`);
              findings++;
            } else seen.set(k, j+1);
          }
        }
      }
      if (started && depth <= 0 && j > i) break;
      j++;
    }
    i = j;
  }
}
console.log(findings === 0 ? '\nPASS - no duplicate when-branches anywhere' : `\nFAIL - ${findings} duplicate branch(es)`);
// This printed FAIL and exited 0, so `set -e` in run.sh saw success and CI went
// green on a real finding -- a checker guarding the bug that blacked out video
// for three releases, which could not itself fail. Every other checker here
// exits on its verdict; this one now does too.
process.exit(findings === 0 ? 0 : 1);
