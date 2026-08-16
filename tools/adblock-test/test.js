// Runs the REAL AD_BLOCK_JS against every ad shape we know about, plus the
// legit floating controls that MUST survive. Regression suite for the weighted
// scorer + the v6.2.5 inset-banner fix.
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const script = fs.readFileSync(path.join(__dirname, 'adblock.js'), 'utf8');

const page = `<!doctype html><html><head><title>Watch Donghua Anime Online</title></head>
<body>
  <div id="site-header">Donghua Fun</div>

  <!-- AD 1: fake notification card. Not fixed, no z-index. -->
  <div id="adcard" style="position:relative">
    <span>New message</span><span>00:34</span>
    <b>Your premium account is activated!</b>
    <p>Please follow the instruction on the next page to proceed.</p>
    <a href="https://someadsite.example/offer">Accept</a><a href="#">Cancel</a>
  </div>

  <!-- AD 2: floating gift widget with <img> + red badge + close. -->
  <div id="giftad" style="position:fixed"><span class="close">x</span>
    <img src="/img/gift.png"><span class="badge">1</span></div>

  <!-- AD 3: HARD case - generic name, NO img tag (CSS background-image), NO DOM
       badge/close. Must still be caught by name+raster+corner+size scoring. -->
  <div id="promo7" class="fx-float-2" style="position:fixed" data-bg="url(/x.png)">
    <a href="https://ad.example/win" class="lucky-draw">&nbsp;</a></div>

  <!-- AD 4: sticky coupon widget linking off-site. -->
  <div id="coupon" class="coupon-box" style="position:sticky">
    <a href="https://promo.example/coupon"><img src="/c.png"></a></div>

  <!-- AD 5: THE SCREENSHOT AD - push-notification "message" card. Inset banner
       ~85% of the viewport wide (this is what the old >=85% width bail-out threw
       away), thumbnail + clickbait headline + "Click Here" + "Hide" + red 1. -->
  <div id="msgcard" style="position:fixed">
    <span class="cnt">1</span>
    <img src="https://cdn.example/fb.jpg">
    <b>&ldquo;Don&rsquo;t worry, here&rsquo;s how to SAVE your FB account if it was hacked</b>
    <a href="https://trk.example/go?c=9">Click Here</a><span class="h">Hide</span>
  </div>

  <!-- LEGIT: site's own scroll-to-top. Fixed + corner, VECTOR icon only. -->
  <div id="totop" style="position:fixed"><svg width="24" height="24"></svg></div>
  <!-- LEGIT: site's real bottom nav (full width). -->
  <div id="nav" style="position:fixed">Home Schedule Donghua Article My Account</div>
  <!-- LEGIT: site's own sticky top header bar (full-bleed, pinned to top). -->
  <div id="topbar" style="position:fixed"><a href="/">Donghua Fun</a> Search</div>
  <!-- LEGIT: a fixed same-site "settings" icon button, vector, no link. -->
  <div id="settings" style="position:fixed"><svg></svg></div>
  <div id="real-content"><h1>Soul Land 2</h1><p>TOP 4 Trending Donghua</p></div>
</body></html>`;

const dom = new JSDOM(page, { url: 'https://donghuafun.com/', runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window;

// jsdom does no layout - synthesize plausible boxes per element.
w.Element.prototype.getBoundingClientRect = function () {
  let b;
  if (this.id === 'nav') b = { width: 900, height: 90, left: 0, top: 1910 };
  else if (this.id === 'topbar') b = { width: 900, height: 110, left: 0, top: 0 };
  // the message card: INSET (left 64) and 85.3% wide - deliberately the exact
  // geometry from the user's screenshot, scaled to this 900x2000 viewport.
  else if (this.id === 'msgcard') b = { width: 768, height: 188, left: 64, top: 308 };
  else if (this.id === 'totop' || this.id === 'settings') b = { width: 44, height: 44, left: 850, top: 1600 };
  else b = { width: 110, height: 110, left: 780, top: 1500 }; // corner banners
  return { width: b.width, height: b.height, top: b.top, left: b.left,
           right: b.left + b.width, bottom: b.top + b.height };
};
Object.defineProperty(w, 'innerWidth', { value: 900 });
Object.defineProperty(w, 'innerHeight', { value: 2000 });
// jsdom returns '' for backgroundImage; emulate the data-bg on AD 3 so the
// raster check sees the CSS background-image these widgets really use.
const realCS = w.getComputedStyle.bind(w);
w.getComputedStyle = function (el, pseudo) {
  const cs = realCS(el, pseudo);
  if (el && el.getAttribute && el.getAttribute('data-bg')) {
    return new Proxy(cs, { get: (t, k) => k === 'backgroundImage' ? el.getAttribute('data-bg') : t[k] });
  }
  return cs;
};

let threw = null;
try { w.eval(script); } catch (e) { threw = e; }
console.log('script threw at load :', threw ? (threw.name + ': ' + threw.message) : 'no');

setTimeout(() => {
  const g = id => w.document.getElementById(id);
  const res = {
    'AD1 fake-notification card removed  ': g('adcard') === null,
    'AD2 gift widget (img+badge) removed ': g('giftad') === null,
    'AD3 bg-image promo widget removed   ': g('promo7') === null,
    'AD4 sticky coupon widget removed    ': g('coupon') === null,
    'AD5 push "message" card removed     ': g('msgcard') === null,
    'LEGIT scroll-to-top (vector) kept   ': g('totop') !== null,
    'LEGIT settings icon (vector) kept   ': g('settings') !== null,
    'LEGIT bottom nav kept               ': g('nav') !== null,
    'LEGIT full-bleed top header kept    ': g('topbar') !== null,
    'LEGIT page content kept             ': g('real-content') !== null,
  };
  let ok = true;
  for (const [k, v] of Object.entries(res)) {
    console.log(`${v ? 'PASS' : 'FAIL'}  ${k}`);
    if (!v) ok = false;
  }
  const zapped = w.eval('window.__sfZap ? window.__sfZap() : -1');
  console.log('manual zap available        :', zapped >= 0 ? 'yes' : 'NO');
  console.log(ok ? '\nALL CHECKS PASSED' : '\nSOME CHECKS FAILED');
  process.exit(ok ? 0 : 1);
}, 2500);
