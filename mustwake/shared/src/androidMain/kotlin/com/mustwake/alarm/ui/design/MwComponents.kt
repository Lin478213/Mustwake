package com.mustwake.alarm.ui.design

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
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
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
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

@Composable
fun MwText(
    text: String,
    modifier: Modifier = Modifier,
    typo: MwTypo = MwTypo.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val styles = MiuixTheme.textStyles
    val style = when (typo) {
        MwTypo.Display -> styles.title1
        MwTypo.Title -> styles.title2
        MwTypo.Headline -> styles.headline2
        MwTypo.Body -> styles.main
        MwTypo.Label -> styles.subtitle
        MwTypo.Caption -> styles.footnote1
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
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        MwText(text = text, typo = MwTypo.Label)
    }
}

@Composable
fun MwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled) {
        MwText(text = text, typo = MwTypo.Label)
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun MwSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        modifier = modifier,
        summary = summary,
    )
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
 * Bottom navigation with two independent style axes:
 * - [floating]: centered pill instead of a full-width bar;
 * - [glassBackdrop]: liquid-glass surface (blur + tint + highlight); `null` renders solid.
 *
 * The two combinations are independent: floating solid, full-width glass, floating glass, etc.
 */
@Composable
fun MwBottomBar(
    tabs: List<MwTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    floating: Boolean,
    glassBackdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    if (floating) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (glassBackdrop != null) {
                            Modifier.mwGlass(glassBackdrop, RoundedCornerShape(28.dp))
                        } else {
                            Modifier.background(
                                color = MiuixTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(28.dp),
                            )
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    FloatingNavigationBarItem(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        icon = tab.icon,
                        label = tab.label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (glassBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .mwGlass(
                        backdrop = glassBackdrop,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    ),
            )
        }
        NavigationBar(
            color = if (glassBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
        ) {
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
