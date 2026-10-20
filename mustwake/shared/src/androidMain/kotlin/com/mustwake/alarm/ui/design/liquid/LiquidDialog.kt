package com.mustwake.alarm.ui.design.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * Ported 1:1 from AndroidLiquidGlass `catalog/destinations/DialogContent.kt` (the glass shell).
 *
 * Hosted by `App` outside the captured screen layer so the panel can refract the content
 * beneath it without self-capture; see `MwDialogSpec` in MwComponents.
 */
@Composable
fun LiquidDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = isLiquidLightTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = liquidAccent()
    val agslHighlight = com.mustwake.alarm.ui.design.LocalMwAgslHighlightEnabled.current
    val containerColor =
        if (isLightTheme) LiquidStyle.DialogContainerLight else LiquidStyle.DialogContainerDark
    val dimColor =
        if (isLightTheme) LiquidStyle.DialogDimLight else LiquidStyle.DialogDimDark

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(dimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(LiquidStyle.DialogMargin)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(LiquidStyle.DialogCorner) },
                    effects = {
                        colorControls(
                            brightness = if (isLightTheme) 0.2f else 0f,
                            saturation = 1.5f
                        )
                        blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                        if (agslHighlight) {
                            lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                        }
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .fillMaxWidth()
        ) {
            BasicText(
                title,
                Modifier.padding(28f.dp, 24f.dp, 28f.dp, 12f.dp),
                style = LiquidStyle.DialogTitle.copy(color = contentColor),
            )

            BasicText(
                text,
                Modifier
                    .then(
                        if (isLightTheme) {
                            Modifier
                        } else {
                            Modifier.graphicsLayer(blendMode = BlendMode.Plus)
                        }
                    )
                    .padding(24f.dp, 12f.dp, 24f.dp, 12f.dp),
                style = LiquidStyle.Label.copy(color = contentColor.copy(alpha = 0.68f)),
                maxLines = 5
            )

            Row(
                Modifier
                    .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16f.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .clip(Capsule())
                        .background(containerColor.copy(alpha = 0.2f))
                        .clickable(onClick = onDismiss)
                        .height(LiquidStyle.ButtonHeight)
                        .weight(1f)
                        .padding(horizontal = LiquidStyle.ButtonHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(4f.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        dismissText,
                        style = LiquidStyle.Body.copy(color = contentColor),
                    )
                }
                Row(
                    Modifier
                        .clip(Capsule())
                        .background(accentColor)
                        .clickable(onClick = onConfirm)
                        .height(LiquidStyle.ButtonHeight)
                        .weight(1f)
                        .padding(horizontal = LiquidStyle.ButtonHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(4f.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        confirmText,
                        style = LiquidStyle.Body.copy(color = Color.White),
                    )
                }
            }
        }
    }
}
