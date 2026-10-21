package com.mustwake.alarm.ui.design

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.mustwake.alarm.ui.design.liquid.LiquidBottomTab
import com.mustwake.alarm.ui.design.liquid.LiquidBottomTabs
import com.mustwake.alarm.ui.design.liquid.LiquidButton
import com.mustwake.alarm.ui.design.liquid.LiquidDialog
import com.mustwake.alarm.ui.design.liquid.LiquidSlider
import com.mustwake.alarm.ui.design.liquid.LiquidStyle
import com.mustwake.alarm.ui.design.liquid.LiquidToggle

/**
 * The only file in the app that is allowed to import Miuix directly.
 *
 * Screens depend on this thin adapter so an experimental Miuix API change (or a future UI stack
 * swap) stays contained. Keep the surface minimal and semantic.
 */
@Composable
fun MwTheme(content: @Composable () -> Unit) {
    MiuixTheme(
        colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

/**
 * False in JVM screenshot tests: backdrop's [Highlight.Default] compiles an AGSL shader with a
 * `layout(color)` uniform, which Robolectric NATIVE graphics cannot parse. When false, [mwGlass]
 * uses [Highlight.Plain], which paints the same edge stroke without a runtime shader.
 */
@VisibleForTesting
val LocalMwAgslHighlightEnabled = staticCompositionLocalOf { true }

/**
 * Forces the non-AGSL rendering paths (plain rounded rectangles, plain glass highlight).
 *
 * Miuix's squircle is an AGSL [android.graphics.RuntimeShader] that uses the `layout(color)`
 * uniform qualifier; Robolectric NATIVE graphics cannot compile that shader and throws. The
 * same applies to backdrop's [Highlight.Default]. Screenshot tests wrap content in this so they
 * reach the libraries' own non-shader paths instead of crashing. On-device rendering is unchanged.
 */
@VisibleForTesting
@Composable
fun MwSquircleFallback(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSquircleEnabled provides false,
        LocalMwAgslHighlightEnabled provides false,
        content = content,
    )
}

/**
 * Fills the viewport with the theme background.
 *
 * On device the Activity window is tinted by the system theme; Robolectric's window stays
 * light, which leaks into screenshot edges. Test hosts wrap content in this (inside [MwTheme])
 * so light/dark baselines have a consistent canvas. Production screens keep the platform
 * window background.
 */
@VisibleForTesting
@Composable
fun MwThemeBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
        content = { content() },
    )
}

enum class MwTypo { Display, Title, Headline, Body, Label, Caption }

/**
 * Type scale aligned 1:1 with AndroidLiquidGlass (docs/ui.md § Typography & margins).
 * Applied globally, not gated by the liquid-glass toggle.
 */
@Composable
fun MwText(
    text: String,
    modifier: Modifier = Modifier,
    typo: MwTypo = MwTypo.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val style = when (typo) {
        MwTypo.Display -> LiquidStyle.Display
        MwTypo.Title -> LiquidStyle.DialogTitle
        MwTypo.Headline -> LiquidStyle.BodyLarge
        MwTypo.Body -> LiquidStyle.Body
        MwTypo.Label -> LiquidStyle.Label
        MwTypo.Caption -> LiquidStyle.Caption
    }
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun MwScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = fab,
        content = content,
    )
}

@Composable
fun MwTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                }
            }
        },
        actions = actions,
    )
}

/** Set by [App] from the liquid-glass toggle; cards use it for translucency + edge highlight. */
val LocalMwGlassEnabled = staticCompositionLocalOf { false }

@Composable
fun MwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalMwGlassEnabled.current) {
        val shape = RoundedCornerShape(16.dp)
        Card(
            modifier = modifier.border(0.5.dp, Color.White.copy(alpha = 0.18f), shape),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surface.copy(alpha = 0.72f),
            ),
            onClick = onClick,
            content = content,
        )
    } else {
        Card(modifier = modifier, onClick = onClick, content = content)
    }
}

@Composable
fun MwSectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(text = text, modifier = modifier)
}

@Composable
fun MwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalMwGlassEnabled.current) {
        val accent = if (isSystemInDarkTheme()) LiquidStyle.AccentDark else LiquidStyle.AccentLight
        LiquidButton(
            onClick = onClick,
            backdrop = emptyBackdrop(),
            modifier = modifier,
            enabled = enabled,
            tint = accent,
        ) {
            BasicText(text = text, style = LiquidStyle.Label.copy(color = Color.White))
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            MwText(text = text, typo = MwTypo.Label)
        }
    }
}

@Composable
fun MwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalMwGlassEnabled.current) {
        LiquidButton(
            onClick = onClick,
            backdrop = emptyBackdrop(),
            modifier = modifier,
            enabled = enabled,
            surfaceColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.55f),
        ) {
            BasicText(text = text, style = LiquidStyle.Label.copy(color = MiuixTheme.colorScheme.onSurface))
        }
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) {
            MwText(text = text, typo = MwTypo.Label)
        }
    }
}

@Composable
fun MwChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.textButtonColorsPrimary()
        } else {
            ButtonDefaults.textButtonColors()
        },
    )
}

@Composable
fun MwSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalMwGlassEnabled.current) {
        // Stable getter contract (see MwBottomBar): read state behind a remembered lambda.
        val checkedState = rememberUpdatedState(checked)
        val selected = remember { { checkedState.value } }
        Box(modifier = modifier.alpha(if (enabled) 1f else 0.4f)) {
            LiquidToggle(
                selected = selected,
                onSelect = { if (enabled) onCheckedChange(it) },
                backdrop = emptyBackdrop(),
            )
        }
    } else {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

@Composable
fun MwSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalMwGlassEnabled.current) {
        // Stable getter contract (see MwBottomBar).
        val checkedState = rememberUpdatedState(checked)
        val selected = remember { { checkedState.value } }
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = title, style = LiquidStyle.Body.copy(color = MiuixTheme.colorScheme.onSurface))
                if (summary != null) {
                    BasicText(
                        text = summary,
                        style = LiquidStyle.Label.copy(color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f)),
                    )
                }
            }
            LiquidToggle(
                selected = selected,
                onSelect = onCheckedChange,
                backdrop = emptyBackdrop(),
            )
        }
    } else {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            modifier = modifier,
            summary = summary,
        )
    }
}

@Composable
fun MwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
    )
}

@Composable
fun MwNumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { it.toString() },
    itemHeight: Dp = 40.dp,
) {
    NumberPicker(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        range = range,
        label = label,
        visibleItemCount = 3,
        itemHeight = itemHeight,
    )
}

@Composable
fun MwProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier,
        progress = progress.coerceIn(0f, 1f),
    )
}

@Composable
fun MwFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

data class MwTab(val label: String, val icon: ImageVector)

/**
 * Bottom navigation with a single visual axis (liquid glass toggle):
 * - glass on  → 1:1 `LiquidBottomTabs` (floating capsule, drag thumb, refraction);
 * - glass off → full-width Miuix `NavigationBar`.
 */
@Composable
fun MwBottomBar(
    tabs: List<MwTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    glassBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (glassBackdrop != null) {
        val contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
        // Contract of the reference components: the getters must be STABLE (read mutable state
        // behind a remembered lambda). A lambda capturing an Int is recreated on every selection
        // change, which re-keys LiquidBottomTabs' internal remember/LaunchedEffect and kills the
        // snapshotFlow chain that drives the thumb animation (thumb would freeze in place).
        val selectedIndexState = rememberUpdatedState(selectedIndex)
        val selectedTabIndex = remember { { selectedIndexState.value } }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = LiquidStyle.BarSideMargin, vertical = 8.dp),
        ) {
            LiquidBottomTabs(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onSelect,
                backdrop = glassBackdrop,
                tabsCount = tabs.size,
                modifier = Modifier.fillMaxWidth(),
            ) {
                tabs.forEachIndexed { index, tab ->
                    LiquidBottomTab(onClick = { onSelect(index) }) {
                        Box(
                            Modifier
                                .size(LiquidStyle.TabIconSize)
                                .paint(
                                    rememberVectorPainter(tab.icon),
                                    colorFilter = ColorFilter.tint(contentColor),
                                )
                        )
                        BasicText(
                            tab.label,
                            style = LiquidStyle.Caption.copy(color = contentColor),
                        )
                    }
                }
            }
        }
        return
    }

    // BUG-2: the solid branch dropped `modifier`, so the bar lost `align(BottomCenter)` from
    // App and rendered at the content Box's topStart (over the first card). Keep the modifier.
    NavigationBar(modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

/**
 * Slider-shaped control: `LiquidSlider` when liquid glass is on, `MwNumberPicker` otherwise.
 * Used for goal-steps values (1:1 slider skin from the reference project).
 */
@Composable
fun MwSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
) {
    if (LocalMwGlassEnabled.current) {
        // Stable getter contract (see MwBottomBar).
        val valueState = rememberUpdatedState(value)
        val valueGetter = remember { { valueState.value.toFloat() } }
        LiquidSlider(
            value = valueGetter,
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            visibilityThreshold = 1f,
            backdrop = emptyBackdrop(),
            modifier = modifier,
        )
    } else {
        MwNumberPicker(value = value, onValueChange = onValueChange, range = range, modifier = modifier)
    }
}

/**
 * Titled slider row: `LiquidSlider` under liquid glass, `+/-` stepper otherwise.
 * Stepper layout matches the settings rows so non-glass users see no change.
 */
@Composable
fun MwSliderRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    if (LocalMwGlassEnabled.current) {
        // Stable getter contract (see MwBottomBar).
        val valueState = rememberUpdatedState(value)
        val valueGetter = remember { { valueState.value.toFloat() } }
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row {
                MwText(text = title, typo = MwTypo.Body, modifier = Modifier.weight(1f))
                MwText(text = "$value $suffix", typo = MwTypo.Caption)
            }
            LiquidSlider(
                value = valueGetter,
                onValueChange = { raw ->
                    val snapped = (raw / step).toInt().let { base ->
                        val candidates = listOf(base * step, (base + 1) * step)
                        candidates.minByOrNull { kotlin.math.abs(it - raw) } ?: raw.toInt()
                    }.coerceIn(range)
                    onChange(snapped)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                visibilityThreshold = step.toFloat(),
                backdrop = emptyBackdrop(),
            )
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                MwText(text = title, typo = MwTypo.Body)
                MwText(text = "$value $suffix", typo = MwTypo.Caption)
            }
            MwSecondaryButton(
                text = "−",
                modifier = Modifier.width(56.dp),
                enabled = value > range.first,
                onClick = { onChange((value - step).coerceAtLeast(range.first)) },
            )
            MwSecondaryButton(
                text = "+",
                modifier = Modifier.width(56.dp),
                enabled = value < range.last,
                onClick = { onChange((value + step).coerceAtMost(range.last)) },
            )
        }
    }
}

data class MwDialogSpec(
    val title: String,
    val text: String,
    val confirmText: String,
    val onConfirm: () -> Unit,
    val dismissText: String,
    val onDismiss: () -> Unit,
)

class MwDialogState {
    var spec: MwDialogSpec? by mutableStateOf(null)
}

val LocalMwDialogState = staticCompositionLocalOf { MwDialogState() }

/**
 * Dialog overlay hosted by [com.mustwake.alarm.App] OUTSIDE the captured screen layer so the
 * glass panel can refract the content beneath it without self-capture.
 */
@Composable
fun MwDialogHost(glassBackdrop: Backdrop?, state: MwDialogState) {
    val spec = state.spec ?: return
    if (glassBackdrop != null) {
        LiquidDialog(
            title = spec.title,
            text = spec.text,
            confirmText = spec.confirmText,
            onConfirm = {
                state.spec = null
                spec.onConfirm()
            },
            dismissText = spec.dismissText,
            onDismiss = {
                state.spec = null
                spec.onDismiss()
            },
            onDismissRequest = {
                state.spec = null
                spec.onDismiss()
            },
            backdrop = glassBackdrop,
        )
    } else {
        MwSolidDialog(
            spec = spec,
            onConfirm = {
                state.spec = null
                spec.onConfirm()
            },
            onDismiss = {
                state.spec = null
                spec.onDismiss()
            },
        )
    }
}

@Composable
private fun MwSolidDialog(
    spec: MwDialogSpec,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = LiquidStyle.DialogMargin)
                .fillMaxWidth(),
        ) {
            MwText(text = spec.title, typo = MwTypo.Title, modifier = Modifier.padding(24.dp, 20.dp, 24.dp, 8.dp))
            MwText(
                text = spec.text,
                typo = MwTypo.Label,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                modifier = Modifier.padding(horizontal = 24.dp),
                maxLines = 5,
            )
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MwSecondaryButton(text = spec.dismissText, onClick = onDismiss, modifier = Modifier.weight(1f))
                MwPrimaryButton(text = spec.confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** True when the platform can render runtime blur and the device is not low-RAM. */
@Composable
fun mwGlassSupported(): Boolean {
    val context = LocalContext.current
    val lowRam = remember(context) {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }
    return !lowRam && isRenderEffectSupported()
}

/**
 * Liquid-glass background: blurred captured content + translucent tint + lit edge.
 * Falls back to a near-opaque surface when runtime blur is unavailable.
 */
@Composable
fun Modifier.mwGlass(backdrop: Backdrop, shape: Shape): Modifier {
    val surface = MiuixTheme.colorScheme.surface
    if (!mwGlassSupported()) {
        return background(surface.copy(alpha = 0.94f), shape)
    }
    val blurRadius = with(LocalDensity.current) { 24.dp.toPx() }
    val agslHighlight = LocalMwAgslHighlightEnabled.current
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(blurRadius) },
            highlight = {
                if (agslHighlight) {
                    Highlight.Default.copy(alpha = 0.75f)
                } else {
                    Highlight.Plain.copy(alpha = 0.75f)
                }
            },
            shadow = null,
        )
        .background(surface.copy(alpha = 0.55f), shape)
}

@Composable
fun MwIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/** Spacing helper so screens do not need to import Miuix paddings. */
@Composable
fun MwSection(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        content = content,
    )
}
