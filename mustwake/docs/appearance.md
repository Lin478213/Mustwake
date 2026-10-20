# Appearance: liquid glass (1:1 from AndroidLiquidGlass)

Status: **rework 2026-10-20** — the earlier hand-rolled glass (blur 24dp + 55% tint + stroke) is
replaced by a 1:1 port of [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
(`kmp` branch, cloned at repo root `AndroidLiquidGlass/`, gitignored as upstream reference).

## Single toggle

| setting | default | effect |
|---|---|---|
| `liquidGlassEnabled` | `false` | **one switch controls everything**: floating liquid capsule bottom tabs + liquid toggle/button/slider/dialog + glass cards |

- ON  → full liquid experience (see component spec below)
- OFF → previous plain styles (full-width Miuix `NavigationBar`, Miuix controls)

The former `floatingBottomBarEnabled` toggle was **merged away** (user decision 2026-10-20).
The `AppSettings` field remains for backup schema v1 round-trip but is no longer read by the UI
and is deprecated (`docs/data-layer.md`).

## Component spec (1:1 parameters)

All values extracted from the reference sources (`app/src/commonMain/.../catalog/`), see
`ui/design/liquid/LiquidStyle.kt` for the constants and the ported files for behavior.

### Bottom tabs (`LiquidBottomTabs` / `LiquidBottomTab`)
- side margin **36dp**; outer capsule height **64dp**, inner padding **4dp**; inner bar/thumb **56dp**
- tab item: icon **28dp**, label **12sp**, icon-label gap **2dp**
- glass: `vibrancy(); blur(8dp); lens(24dp, 24dp)`; surface `#FAFAFA@40%` light / `#121212@40%` dark
- thumb: combined backdrop (page + accent-tinted tab layer) + `lens(10dp·p, 14dp·p, chromaticAberration)`
  + `Highlight.Default·p` + `Shadow·p` + `InnerShadow(8dp·p, p)`
- dynamics: `DampedDragAnimation` (drag-to-switch, press scale 78/56, velocity stretch),
  `InteractiveHighlight` (pointer/selection-following light), 4dp drag offset rebound,
  selected tab content scale 1→1.2

### Buttons (`LiquidButton`)
- height **48dp**, horizontal padding **16dp**, content gap **8dp**, `Capsule()`
- glass: `vibrancy(); blur(2dp); lens(12dp, 24dp)`; drag-follow press deformation

### Toggle (`LiquidToggle`)
- track **64×28dp** (color lerps track→accent with value), thumb **40×24dp**, drag width 20dp
- accent `#34C759` light / `#30D158` dark; press scale 1.5, chromatic lens on press

### Slider (`LiquidSlider`)
- track **6dp** capsule, thumb **40×24dp** glass capsule; tap-to-jump + drag

### Dialog (`LiquidDialog`)
- outer margin **40dp**, `RoundedRectangle(48dp)`
- glass: `colorControls(brightness 0.2 light/saturation 1.5); blur(16dp light/8dp dark);
  lens(24dp, 48dp, depthEffect=true)` + `Highlight.Plain`; scrim `#29293A@23%` light / `#121212@56%` dark

### Typography / margins (global, always applied — not toggle-gated)
- type scale (BasicText): Display **28sp Medium** · dialog title **24sp Medium** · body large **17sp** ·
  body/button **15–16sp** · secondary **15sp @68%** · labels **12sp**
- accent `#0088FF` light / `#0091FF` dark (15sp Medium for accent text)
- bottom bar side margin 36dp; content margins follow the reference scaffold (see `docs/ui.md`)

## Degradation

`mwGlassSupported()` = `isRenderEffectSupported() && !isLowRamDevice` (unchanged).

- unsupported / low-RAM → glass surfaces fall back to a 94% opaque surface; liquid controls
  fall back to their plain (non-glass) drawing
- Robolectric / JVM screenshots: `MwSquircleFallback` disables AGSL paths (`layout(color)`
  shaders cannot compile there). `lens()` no-ops when runtime shaders are unsupported;
  `Highlight` uses `Plain`. Screenshots therefore show the degraded path — real look is
  verified on the emulator/device.
- `LiquidToggle`/`LiquidSlider` glass thumbs still draw their non-shader parts in tests.

## Where liquid components are used

| component | MustWake surface |
|---|---|
| LiquidBottomTabs | main nav (闹钟/历史/设置) |
| LiquidToggle | all settings switches |
| LiquidButton | settings actions, backup import/export, ringing snooze |
| LiquidSlider | goal-steps control (edit screen + settings default) when glass ON |
| LiquidDialog | backup REPLACE confirmation |

Behavior is identical between toggle states; only the skin/interaction animation changes.

## Tests

- UI smoke: glass ON/OFF two states + component cases (Robolectric composes degraded path)
- Roborazzi baselines re-recorded for the new UI (`recordRoborazziDebug`)
