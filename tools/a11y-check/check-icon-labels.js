// Flags icon-only controls that a screen reader cannot announce.
//
// A null contentDescription is CORRECT for a decorative glyph sitting beside a
// text label — that is why a blanket "label everything" pass would make TalkBack
// noisier, not better. It is wrong for an IconButton, where the icon IS the
// control: TalkBack announces "button", unlabelled, and the user has no way to
// know what it does.
//
// The player is the worst case, being almost entirely icon-only controls.
//
// ── Why this is a brace-aware parser and not a set of line regexes ───────────
//
// The first version scanned line by line: find `IconButton(`, then walk forward
// counting parens and stop when the depth returned to zero. That silently missed
// every IconButton written across several lines, because the parameter list
// closes BEFORE the trailing lambda that holds the icon:
//
//     IconButton(
//         onClick = { ... },        <- depth returns to 0 on the next line
//         enabled = canGoForward
//     ) {                           <- scan already gave up here
//         Icon(Icons.Rounded.ArrowForward, contentDescription = "Forward")
//     }
//
// It also missed any `Icon(` whose arguments were wrapped onto the next line,
// since the regex had to match within a single line.
//
// Both failures under-count, which means they can only ever turn a real finding
// into a PASS. A checker that silently reports success is worse than no checker,
// so this walks the actual token structure instead: strings and comments are
// skipped, the trailing lambda is treated as part of the call, and anything that
// cannot be parsed is reported as UNKNOWN rather than quietly dropped.
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

// Blanks out comments and string literals so their braces, parens and commas
// cannot be mistaken for code structure. Length is preserved, so every index
// still lines up with the original source.
function mask(src) {
  const out = src.split('');
  let i = 0;
  const blank = (from, to) => {
    for (let k = from; k < to && k < out.length; k++) {
      if (out[k] !== '\n') out[k] = ' ';
    }
  };
  while (i < src.length) {
    if (src.startsWith('//', i)) {
      const end = src.indexOf('\n', i);
      blank(i, end === -1 ? src.length : end);
      i = end === -1 ? src.length : end;
    } else if (src.startsWith('/*', i)) {
      const end = src.indexOf('*/', i + 2);
      const stop = end === -1 ? src.length : end + 2;
      blank(i, stop);
      i = stop;
    } else if (src.startsWith('"""', i)) {
      const end = src.indexOf('"""', i + 3);
      const stop = end === -1 ? src.length : end + 3;
      blank(i, stop);
      i = stop;
    } else if (src[i] === '"') {
      let j = i + 1;
      while (j < src.length && src[j] !== '"') {
        if (src[j] === '\\') j++;
        if (src[j] === '\n') break;
        j++;
      }
      blank(i, j + 1);
      i = j + 1;
    } else {
      i++;
    }
  }
  return out.join('');
}

// End index of a call starting at the '(' in `masked`, including a trailing
// lambda block if one follows.
function endOfCall(masked, parenIdx) {
  let depth = 0;
  let i = parenIdx;
  for (; i < masked.length; i++) {
    if (masked[i] === '(') depth++;
    else if (masked[i] === ')') {
      depth--;
      if (depth === 0) { i++; break; }
    }
  }
  if (depth !== 0) return -1;
  let j = i;
  while (j < masked.length && /\s/.test(masked[j])) j++;
  if (masked[j] === '{') {
    let bd = 0;
    for (i = j; i < masked.length; i++) {
      if (masked[i] === '{') bd++;
      else if (masked[i] === '}') {
        bd--;
        if (bd === 0) { i++; break; }
      }
    }
    if (bd !== 0) return -1;
  }
  return i;
}

// Top-level (depth-1) argument slices of a call starting at its '('.
function argsOf(masked, raw, parenIdx) {
  const args = [];
  let depth = 0, start = parenIdx + 1;
  for (let i = parenIdx; i < masked.length; i++) {
    const c = masked[i];
    if (c === '(' || c === '{' || c === '[') depth++;
    else if (c === ')' || c === '}' || c === ']') {
      depth--;
      if (depth === 0) { args.push(raw.slice(start, i)); break; }
    } else if (c === ',' && depth === 1) {
      args.push(raw.slice(start, i));
      start = i + 1;
    }
  }
  return args.map(a => a.trim());
}

// True when an identifier occurrence is a standalone word, so `IconButton(`
// is never mistaken for `Icon(`.
function isWordStart(s, idx, word) {
  const before = idx === 0 ? '' : s[idx - 1];
  const after = s[idx + word.length];
  return !/[A-Za-z0-9_]/.test(before || '') && after === '(';
}

const ROOT = 'app/src/main/java/com/streamflow';
let unlabelled = 0, labelled = 0, unknown = 0;
const perFile = {};
const unknownFile = {};

for (const file of walk(ROOT)) {
  const raw = fs.readFileSync(file, 'utf8');
  const masked = mask(raw);
  const lineOf = idx => raw.slice(0, idx).split('\n').length;

  for (let i = 0; i < masked.length; i++) {
    if (!masked.startsWith('IconButton', i)) continue;
    if (!isWordStart(masked, i, 'IconButton')) continue;

    const open = masked.indexOf('(', i);
    const end = endOfCall(masked, open);
    if (end === -1) {
      unknown++;
      (unknownFile[file] = unknownFile[file] || []).push(lineOf(i));
      continue;
    }

    // First Icon( inside this IconButton's whole span, lambda included.
    let iconIdx = -1;
    for (let k = open; k < end; k++) {
      if (masked.startsWith('Icon', k) && isWordStart(masked, k, 'Icon')) {
        iconIdx = k;
        break;
      }
    }
    if (iconIdx === -1) {
      // No icon at all — e.g. an IconButton wrapping a Text or a Box. Not an
      // icon-only control, so out of scope rather than a failure.
      continue;
    }

    const iconOpen = masked.indexOf('(', iconIdx);
    const args = argsOf(masked, raw, iconOpen);
    if (args.length < 2) {
      unknown++;
      (unknownFile[file] = unknownFile[file] || []).push(lineOf(iconIdx));
      continue;
    }

    // Named argument wins wherever it sits; otherwise it is positional arg 2.
    const named = args.find(a => /^contentDescription\s*=/.test(a));
    const desc = (named ? named.replace(/^contentDescription\s*=/, '') : args[1]).trim();

    if (desc === 'null') {
      unlabelled++;
      (perFile[file] = perFile[file] || []).push(lineOf(iconIdx));
    } else {
      labelled++;
    }
    i = end - 1;
  }
}

const files = Object.keys(perFile).sort((a, b) => perFile[b].length - perFile[a].length);
for (const f of files) {
  console.log(`${perFile[f].length.toString().padStart(3)}  ${f}`);
  console.log(`     lines: ${perFile[f].join(', ')}`);
}
for (const f of Object.keys(unknownFile)) {
  console.log(`  ?  ${f} — could not parse at lines: ${unknownFile[f].join(', ')}`);
}

console.log('');
console.log(`labelled icon buttons   : ${labelled}`);
console.log(`unlabelled icon buttons : ${unlabelled}`);
console.log(`unparsed (investigate)  : ${unknown}`);
console.log('');
const ok = unlabelled === 0 && unknown === 0;
console.log(
  ok
    ? 'PASS - every icon-only button has a screen-reader label'
    : unknown > 0
      ? `FAIL - ${unlabelled} unlabelled, ${unknown} unparsed (an unparsed button is an unchecked button)`
      : `FAIL - ${unlabelled} icon-only button(s) are unreachable with TalkBack`
);
process.exit(ok ? 0 : 1);
