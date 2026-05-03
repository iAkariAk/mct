package mct.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport {
        Surface(modifier = Modifier.fillMaxSize()) {
            App()
        }
    }
}

@Composable
fun App() {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("MCT")
            })
        }
    ) {
        TODO()
    }
}