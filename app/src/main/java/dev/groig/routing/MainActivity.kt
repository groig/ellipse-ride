package dev.groig.routing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.groig.routing.ui.MainScreen
import dev.groig.routing.ui.RoutingTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            RoutingTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshBRouter()
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let(::parseLatLon)?.let(vm::offerIncoming)
        }
    }
}
