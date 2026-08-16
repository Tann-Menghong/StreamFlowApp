const fs = require('fs');
const src = fs.readFileSync('app/src/main/java/com/streamflow/ui/player/PlayerScreen.kt', 'utf8').split('\n');
let blocks = [], i = 0;
while (i < src.length) {
  if (/when \(val s = state\)/.test(src[i])) {
    let depth = 0, j = i, branches = [], started = false;
    while (j < src.length) {
      depth += (src[j].match(/{/g)||[]).length - (src[j].match(/}/g)||[]).length;
      const m = src[j].match(/is PlayerUiState\.(\w+)\s*->/);
      if (m && j > i) branches.push([j+1, m[1]]);
      if (src[j].includes('PlayerView(') && j > i) branches.push([j+1, '>>> PlayerView RENDERED HERE']);
      if (src[j].includes('{')) started = true;
      if (started && depth <= 0 && j > i) break;
      j++;
    }
    blocks.push([i+1, branches]); i = j;
  }
  i++;
}
let ok = true;
for (const [start, branches] of blocks) {
  console.log(`when-block at line ${start}:`);
  const seen = new Set();
  for (const [ln, name] of branches) {
    if (name.startsWith('>>>')) { console.log(`    line ${ln}: ${name}`); continue; }
    let dup = '';
    if (seen.has(name)) { dup = '   <-- DUPLICATE (shadowed, unreachable)'; ok = false; }
    seen.add(name);
    console.log(`    line ${ln}: is ${name} ->${dup}`);
  }
  console.log('');
}
console.log('RESULT:', ok ? 'PASS - no shadowed branches' : 'FAIL - duplicate branch found');
process.exit(ok ? 0 : 1);
