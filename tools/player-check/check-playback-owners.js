// Only the files that own playback may construct a player.
//
// Every content tab is a source of videos; exactly one pipeline plays them.
// The Donghua tab is why this exists: it used to be a WebView pointed at a
// third-party site, playing video entirely outside the app's own player, which
// is why nothing on it could be downloaded, favourited, queued or resumed --
// and why, when that site's player showed a black screen, there was nothing in
// StreamFlow to fix. It is now an ordinary source that hands a url to the
// shared player route and constructs nothing.
//
// The failure this guards against is the tempting one: a new tab that "just
// needs a small player of its own". Two playback implementations means every
// fix to buffering, recovery, the media session, background audio or PiP has
// to be made twice, and the second one is always the one that gets forgotten.
//
// The allowlist is deliberately short and each entry has a reason. Adding to it
// should be a decision someone argues for, not a side effect of a new screen.
const fs = require('fs'), path = require('path');

const ALLOWED = new Map([
  ['app/src/main/java/com/streamflow/PlaybackService.kt',
    'the MediaSessionService — the player that background audio, the notification and PiP all run on'],
  ['app/src/main/java/com/streamflow/ui/player/PlayerScreen.kt',
    'the main player UI'],
  ['app/src/main/java/com/streamflow/ui/shorts/ShortsScreen.kt',
    'vertical short-form playback: a pager of independently prepared players, which the session player is not shaped for'],
  ['app/src/main/java/com/streamflow/ui/pdtv/PdTvScreen.kt',
    'a live-TV stream player'],
]);

const SIGNALS = /ExoPlayer\.Builder|MediaItem\.fromUri|MediaItem\.Builder/;

function walk(d, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt')) out.push(p.split(path.sep).join('/'));
  }
  return out;
}

const owners = walk('app/src/main/java').filter((f) =>
  SIGNALS.test(fs.readFileSync(f, 'utf8'))
);

let fail = 0;
console.log(`  ${owners.length} file(s) construct a player\n`);
for (const f of owners) {
  const why = ALLOWED.get(f);
  if (why) {
    console.log(`    ok        ${f}\n              ${why}`);
  } else {
    console.log(`    UNEXPECTED ${f}`);
    fail = 1;
  }
}
for (const [f] of ALLOWED) {
  if (!owners.includes(f)) {
    // Not a failure: playback moving OUT of a file is the direction we want.
    console.log(`    note      ${f} no longer constructs a player; drop it from the allowlist`);
  }
}

if (fail) {
  console.log('');
  console.log('  A screen outside the allowlist is building its own player.');
  console.log('  Content tabs should hand a url to the shared player route instead;');
  console.log('  a second playback implementation has to be fixed twice, forever.');
}
console.log('');
console.log(fail === 0 ? 'PASS - playback is owned by the files that are supposed to own it'
                       : 'FAIL - see above');
process.exit(fail);
