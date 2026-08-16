# Ad-blocker regression suite

Run it:

```bash
bash tools/adblock-test/run.sh
```

## Why this exists

The in-page ad blocker is a raw JavaScript string (`AD_BLOCK_JS`) inside
`AdblockBrowserScreen.kt`, because it has to be injected into *every* WebView
frame via `addDocumentStartJavaScript`. Being a string literal, no Kotlin test
can reach it — and it is the part of the app that has regressed most often.

`run.sh` extracts the literal straight from the Kotlin source and runs it in
jsdom, so the test always exercises the **shipping** script rather than a copy
that has drifted.

## What it covers

Each case is an ad that actually reached a user, or a control that was
wrongly removed by an over-eager fix:

| Case | Why it's here |
|---|---|
| AD1 fake "premium account activated" card | static/relative, so positioning heuristics never saw it |
| AD2 gift widget (img + badge + close) | the classic floating promo |
| AD3 bg-image promo, generic class name | no `<img>`, no DOM badge — CSS `background-image` only |
| AD4 sticky coupon widget | `position: sticky` was not scanned at all |
| AD5 push "message" card | **85.3% wide and inset** — skipped by the old `>= 85%` nav-bar bail-out |
| LEGIT scroll-to-top / settings | small fixed corner icons; must survive (vector, not raster) |
| LEGIT bottom nav / top header | full-bleed bars pinned to an edge; must survive |
| LEGIT page content | sanity check that the sweep isn't nuking the page |

The legit cases matter as much as the ads: every past over-fix removed the
site's own scroll-to-top button.

## Adding a case

When an ad gets through, add it to `page` in `test.js` with its **measured**
geometry from the screenshot (`getBoundingClientRect` is stubbed per-element —
jsdom does no layout). Getting the real numbers in is the whole point: AD5 only
reproduces because it is inset 64px and 768/900 wide.
