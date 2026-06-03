package cast.android.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/**
 * Returns a [confirmed] flag plus a [trigger]. Calling trigger() flips confirmed
 * to true; it auto-resets to false after [durationMs]. Visual-only, fire-on-tap.
 */
@Composable
fun rememberConfirmTrigger(durationMs: Long = 1200): Pair<Boolean, () -> Unit> {
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(confirmed) {
        if (confirmed) {
            delay(durationMs)
            confirmed = false
        }
    }
    return confirmed to { confirmed = true }
}

/**
 * IconButton that briefly morphs [icon] into a primary-tinted checkmark on tap,
 * confirming the action registered. Runs [onClick] immediately (fire-on-tap).
 */
@Composable
fun ConfirmIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val (confirmed, trigger) = rememberConfirmTrigger()
    IconButton(
        onClick = {
            onClick()
            trigger()
        },
        modifier = modifier,
    ) {
        Crossfade(targetState = confirmed, label = "confirmIcon") { isConfirmed ->
            if (isConfirmed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                )
            }
        }
    }
}

/**
 * Material3 Button that briefly swaps its [text] label for [confirmedText] on tap.
 * Runs [onClick] immediately (fire-on-tap).
 */
@Composable
fun ConfirmButton(
    text: String,
    confirmedText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val (confirmed, trigger) = rememberConfirmTrigger()
    Button(
        onClick = {
            onClick()
            trigger()
        },
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(if (confirmed) confirmedText else text)
    }
}
