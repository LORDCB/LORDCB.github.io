package com.lordcb.homelauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** The standard "turns white when focused" look used across the launcher. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    container: Color = Palette.Card,
    radius: Int = 16,
    focusedScale: Float = 1.06f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = Palette.TextPrimary,
            focusedContainerColor = Palette.Focused,
            focusedContentColor = Palette.OnFocused,
            pressedContainerColor = Palette.Focused,
            pressedContentColor = Palette.OnFocused,
        ),
    ) {
        content()
    }
}

/** A pill button with an optional icon. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    FocusCard(onClick = onClick, modifier = modifier, radius = 28, focusedScale = 1.04f) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier, size: Int = 15, maxLines: Int = 1) {
    Text(
        text,
        modifier = modifier,
        fontSize = size.sp,
        color = LocalContentColor.current.copy(alpha = 0.68f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

data class DialogOption(val label: String, val icon: ImageVector? = null, val action: () -> Unit)

@Composable
fun DialogPanel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFF1B2330), contentColor = Palette.TextPrimary),
    ) {
        content()
    }
}

@Composable
fun OptionsDialog(
    title: String,
    subtitle: String?,
    options: List<DialogOption>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        DialogPanel {
            Column(Modifier.width(440.dp).padding(28.dp)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    SecondaryText(subtitle)
                }
                Spacer(Modifier.height(20.dp))
                options.forEachIndexed { i, opt ->
                    ActionButton(
                        label = opt.label,
                        icon = opt.icon,
                        onClick = {
                            onDismiss()
                            opt.action()
                        },
                        modifier = Modifier.fillMaxWidth().let { if (i == 0) it.focusRequester(first) else it },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { first.requestFocus() }
        }
    }
}

/** Text entry that only opens the keyboard on purpose — friendlier with a D-pad. */
@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    hint: String,
    keyboardType: KeyboardType = KeyboardType.Uri,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val submit = {
            onConfirm(value.text)
            onDismiss()
        }
        DialogPanel {
            Column(Modifier.width(620.dp).padding(28.dp)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(16.dp))
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 20.sp),
                    cursorBrush = SolidColor(Palette.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .background(Palette.Background, RoundedCornerShape(12.dp))
                        .border(BorderStroke(2.dp, Palette.Accent), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    decorationBox = { inner ->
                        if (value.text.isEmpty()) {
                            Text(hint, color = Palette.TextSecondary, fontSize = 20.sp)
                        }
                        inner()
                    },
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Save", onClick = submit)
                    ActionButton("Cancel", onClick = onDismiss)
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(120)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
}

@Composable
fun focusedBorder(radius: Int) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(3.dp, Palette.Focused),
        shape = RoundedCornerShape(radius.dp),
    )
)
