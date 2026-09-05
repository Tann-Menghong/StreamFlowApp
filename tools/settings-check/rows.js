// Extracting the settings rows a category page renders.
//
// Shared by check-categories.js and check-search-index.js so the two cannot
// disagree about what a "row" is -- which would let a row be duplicated under
// one checker's definition and indexed under the other's.
//
// This was originally a per-line regex, which silently missed every row whose
// call spans more than one line:
//
//     SettingsSwitchItem(
//         Icons.Rounded.Fullscreen,
//         "Force desktop width",
//
// The label is on the third line, so the line containing the call had no label
// on it and the row was invisible. That is a bad failure for a duplicate check
// -- an unseen row can never be reported as living in two places -- and it hid
// two real rows from the search index.
'use strict';

// Non-greedy across newlines: find the first string literal after the opening
// paren, which is the label in every form these two helpers are called in.
const ROW = /Settings(?:Item|SwitchItem)\(([\s\S]{0,220}?)"((?:[^"\\]|\\.)*)"/g;

// If another call opened between the paren and the quote, the "label" belongs
// to that one instead -- which happens for rows whose label is not a literal
// (SettingsItem(icon, tab.title, ...)). Skip rather than record a wrong owner.
const CROSSED = /Settings(?:Item|SwitchItem|Divider|Card|GroupLabel|Footer)\s*[({]/;

/**
 * Row labels in a slice of source, in source order.
 * @param {string[]} lines  the lines to scan
 * @param {number} baseLine  1-indexed line number `lines[0]` sits at
 * @returns {{label: string, line: number}[]}
 */
function extractRows(lines, baseLine = 1) {
  const text = lines.join('\n');
  const out = [];
  ROW.lastIndex = 0;
  let m;
  while ((m = ROW.exec(text)) !== null) {
    if (CROSSED.test(m[1])) continue;
    const line = baseLine + (text.slice(0, m.index).match(/\n/g) || []).length;
    out.push({ label: m[2], line });
  }
  return out;
}

/**
 * The `when (category)` branches of SettingsCategoryScreen.
 * @returns {{branches: Map<string, {start: number, stop: number}>, whenEnd: number}}
 *          line indices are 0-based into `src`
 */
function categoryBranches(src) {
  const whenLine = src.findIndex((l) => /when \(category\)/.test(l));
  if (whenLine < 0) return null;
  const found = [];
  let whenEnd = src.length;
  for (let i = whenLine + 1; i < src.length; i++) {
    if (/^ {16}else ->/.test(src[i])) { whenEnd = i; break; }
    const m = src[i].match(/^ {16}"([^"]+)" ->/);
    if (m) found.push([m[1], i]);
  }
  const branches = new Map();
  for (let b = 0; b < found.length; b++) {
    const [name, start] = found[b];
    const stop = b + 1 < found.length ? found[b + 1][1] : whenEnd;
    branches.set(name, { start, stop });
  }
  return { branches, whenEnd };
}

module.exports = { extractRows, categoryBranches };
