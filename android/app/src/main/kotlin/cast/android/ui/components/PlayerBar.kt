package cast.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlayerBar() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        tonalElevation = 4.dp,
    ) {}
}
