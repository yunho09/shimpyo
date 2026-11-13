package com.daebangeo.shimpyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daebangeo.shimpyo.ui.ShimpyoRoot
import com.daebangeo.shimpyo.ui.ShimpyoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShimpyoTheme {
                ShimpyoRoot(viewModel<ShimpyoViewModel>())
            }
        }
    }
}
