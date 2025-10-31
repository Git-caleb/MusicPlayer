package com.b230408.musicplayer.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 自定义图标按钮
 */
@Composable
fun CustomIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    buttonSize: androidx.compose.ui.unit.Dp = 48.dp,
    iconTint: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape),
        colors = if (backgroundColor != Color.Unspecified) {
            IconButtonDefaults.iconButtonColors(
                containerColor = backgroundColor
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

/**
 * 自定义圆形按钮（带文字）
 */
@Composable
fun CustomCircularButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    textColor: Color = Color.Unspecified
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CircleShape,
        colors = if (backgroundColor != Color.Unspecified) {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = backgroundColor
            )
        } else {
            androidx.compose.material3.ButtonDefaults.buttonColors()
        }
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = textColor.takeIf { it != Color.Unspecified } ?: androidx.compose.ui.graphics.Color.Unspecified
        )
    }
}
