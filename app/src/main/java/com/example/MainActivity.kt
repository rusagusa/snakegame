package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game.SnakeViewModel
import com.example.ui.screens.SnakeGameScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val snakeViewModel: SnakeViewModel = viewModel(
          factory = SnakeViewModel.provideFactory(applicationContext)
        )
        Scaffold(
          modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
          SnakeGameScreen(
            viewModel = snakeViewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
