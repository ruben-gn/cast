package cast.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    NavigationBar {
        NavigationBarItem(
            selected = currentDest?.hasRoute(Recent::class) == true,
            onClick = {
                navController.navigate(Recent) {
                    popUpTo<Recent> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Headphones, contentDescription = "Recent") },
            label = { Text("Recent") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Catalog::class) == true ||
                currentDest?.hasRoute(PodcastDetail::class) == true,
            onClick = {
                navController.navigate(Catalog) {
                    popUpTo<Recent> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Catalog") },
            label = { Text("Catalog") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Queue::class) == true,
            onClick = {
                navController.navigate(Queue) {
                    popUpTo<Recent> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Queue") },
            label = { Text("Queue") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute(Settings::class) == true,
            onClick = {
                navController.navigate(Settings) {
                    popUpTo<Recent> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
        )
    }
}
