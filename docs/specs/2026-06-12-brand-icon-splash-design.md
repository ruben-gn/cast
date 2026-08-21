# Cast brand mark: launcher icon + splash screen

Date: 2026-06-12
Status: implemented, except the deferred "rising sun" splash (see [Kept for later](#kept-for-later-out-of-scope))

## The mark

A thick, slightly wobbly tangerine ring — a circle whose edge ripples like an audio
waveform — with five thin, far wobblier rainbow rings behind it. Every thin ring is
centered on the main ring's radius, so each one weaves to the inside and outside of it
as it travels around. A navy channel is carved under the main ring so the thin rings
stand clear of it.

All geometry is procedurally generated and seeded — the mark is never hand-edited.

### Locked recipe (canvas 200×200, center 100,100)

Wobble: radius modulated by summed sinusoids, `r(θ) = R·(1 + Σ aᵢ·sin(fᵢθ + pᵢ))`,
240 polyline steps, phases from a seeded mulberry32 PRNG.

| Element | Value |
|---|---|
| Background | `#0D1530` (deep navy) |
| Main ring | `#FF8A4D`, stroke 7, R 60, harmonics (f=9, a=0.022), (f=17, a=0.014), (f=31, a=0.008), seed `20260612` |
| Halo channel | background-color stroke, width 10.5, drawn under the main ring |
| Main ring glow | gaussian blur σ1.6 under source (splash/favicon only; dropped in vector drawables) |
| Thin rings (i = 0..4) | colors `#FFE14D` `#FF5FA8` `#57FFCB` `#5BC8FF` `#C77DFF`, widths `1.4 2.0 2.6 1.7 3.0`, R 60, harmonics (f=2+(i%3), a=0.21+rng·0.05), (f=5+i, a=0.0945+rng·0.03), (f=17+2i, a=0.007), per-ring rotation rng·2π, seed `980311` ("arrangement 4") |
| Thin ring glow | gaussian blur σ0.9 under source (same caveat) |

PRNG draw order matters: per ring, draws are a₁, p₁, a₂, p₂, p₃, rotation.
Main ring draws: p₁, p₂, p₃.

## Deliverables

### 1. Generator script — `tools/brand/generate.js` (Bun, checked in)

Single source of truth. Emits every asset below from the recipe. Rerunning it is the
only sanctioned way to change brand assets.

### 2. Android adaptive launcher icon

- `res/drawable/ic_launcher_foreground.xml` — VectorDrawable, 108dp viewport, the mark
  scaled so the main ring sits at ~24.6dp radius (inside the 33dp safe zone incl. halo)
  and the thin rings reach ~34dp, bleeding toward the typical 36dp circular mask edge.
  Flat (no glow): launchers can't render filters and glow is invisible at icon sizes.
- `res/drawable/ic_launcher_monochrome.xml` — simplified single-color layer for themed
  icons: main ring + two thin rings (mint and violet geometries), white strokes.
- `ic_launcher_background` color → `#0D1530`.
- `mipmap-anydpi-v26/ic_launcher{,_round}.xml` point monochrome at the new drawable.

### 3. Splash screen (concept A — centered emblem)

Android 12+ system SplashScreen, no library, no custom activity:
`values-v31/themes.xml` sets `android:windowSplashScreenBackground = #0D1530`.
The system shows the launcher icon centered on navy; because icon background and
splash background match, it reads as a designed splash. No wordmark (the system
splash API doesn't support one). Pre-12 devices keep the plain launch window.

### 4. Webapp favicon

`favicon.svg` generated with full SVG (glows included — browsers render filters),
served by the Bun webapp and linked from the layout head.

## Kept for later (out of scope)

**Concept C — "rising sun" splash:** the mark anchored at the bottom edge like a
cresting sun, wordmark in the open space above (mockup: `content/splash.html`,
choice `horizon`: motif at (W/2, H−40), scale 1.7, stroke scale 1.3, CAST wordmark
Georgia 600, letter-spacing 6, `#F4EAE4`). Natural homes: the webapp landing/loading
view when the webapp gets its restyle, or an in-app loading state if cold start ever
has a real wait. No artificial delay just to show it.

## Verification

XML well-formedness checked locally; Android build happens on the Mac
(commit + push, then build `installDebug` and eyeball the icon, themed icon, and
splash on device). Favicon checked in a browser via the running webapp.
