package com.example.ui.components
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun TestPull() {
    PullToRefreshBox(isRefreshing = false, onRefresh = {}) {
        Text("Hello")
    }
}
