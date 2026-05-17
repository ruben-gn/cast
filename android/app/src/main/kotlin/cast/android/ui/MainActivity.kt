package cast.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import cast.android.ui.components.PlayerBar
import cast.android.ui.nav.AppNavGraph
import cast.android.ui.nav.BottomNavBar
import cast.android.ui.theme.CastTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CastTheme {
                CastApp()
            }
        }
    }
}

@Composable
private fun CastApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            Column {
                PlayerBar()
                BottomNavBar(navController)
            }
        },
    ) { innerPadding ->
        AppNavGraph(navController = navController, modifier = Modifier.padding(innerPadding))
    }
}
