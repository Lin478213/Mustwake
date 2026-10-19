# Appearance toggles and liquid glass (T6)

Status: **complete** (2026-10-19). Visual verification on a device is still pending (T8).

## Toggles

| setting | default | effect |
|---|---|---|
| `floatingBottomBarEnabled` | `false` | bottom navigation becomes a centered floating pill |
| `liquidGlassEnabled` | `false` | bars/cards use translucent glass surfaces with a lit edge |

They are independent: `floating × glass` produces four valid combinations, all covered by
Robolectric smoke tests. Both values live in DataStore (`AppSettings`) and survive restarts.

## Floating bottom bar

Miuix's `FloatingNavigationBar` paints its own opaque pill, so it cannot host a backdrop without
fighting that background. The adapter (`MwComponents.kt`) composes the pill from
`FloatingNavigationBarItem`s inside a `Row` with `RoundedCornerShape(28.dp)`:

```
floating + solid  -> surfaceContainer pill
floating + glass  -> blurred captured content + 55% tint + Highlight stroke
```

The standard bar is still `Miuix NavigationBar` (transparent when glass is on) with a glass layer
behind it.

## Liquid glass

`io.github.kyant0:backdrop` 2.0.1 has no high-level component, so the glass is composed from:

1. `rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)` on the **screen subtree only** (list /
   settings / history), so the bar never captures itself and the blur cannot feed back;
2. `Modifier.drawBackdrop(backdrop, shape) { blur(24.dp) }` with `Highlight.Default`;
3. a translucent `surface` tint (55%) over the blur.

Cards use a lighter-weight glass: translucent `surface` (72%) + 0.5 dp white edge. Blurring them
would self-capture the screen layer and produce ghosting, so blur is reserved for the bar that
overlays scrolling content.

### Degradation

`mwGlassSupported()` = `isRenderEffectSupported() && !ActivityManager.isLowRamDevice`.

- unsupported / low-RAM → glass surfaces fall back to a 94% opaque surface (still readable, no
  crash); the settings summary explains this to the user;
- cards keep the translucent tint + edge (cheap, no shader);
- blur is only requested when the toggle is on, so the default path allocates no capture.

## Layout consequences

- The bar is now an overlay inside the content area (not Scaffold's `bottomBar`), so lists scroll
  beneath it and the blur has content to sample.
- List/history/settings reserve 120 dp bottom padding; the FAB sits 112 dp above the bottom.
- Bar height/padding is handled by Miuix items, so the floating pill keeps MIUI spacing.

## Tests

`:androidApp:testDebugUnitTest` — 23 tests, including 5 UI smoke tests:

| test | combination |
|---|---|
| default | floating off, glass off |
| floating only | on / off |
| glass only | off / on |
| both | on / on |
| ringing screen | composes with extras |

Total suite: **120 tests** (data 97, app 23). Robolectric cannot judge blur quality; it only proves
the composition path (including the glass draw) does not crash.

## Known gaps

- Real-device blur quality, contrast and frame rate are **unverified** (no device).
- Dialogs are not implemented yet, so the "glass dialog" part of the spec is deferred until a
  dialog is introduced (T7 permission sheets).
- Monet dynamic color is still not enabled; theme follows light/dark only.
- Low-RAM fallback is a capability guess; on-device frame timing may require lowering the 24 dp
  blur radius.
