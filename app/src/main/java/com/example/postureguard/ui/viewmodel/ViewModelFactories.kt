package com.example.postureguard.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.postureguard.AppContainer
import com.example.postureguard.PostureGuardApp

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as PostureGuardApp).container

inline fun <reified VM : ViewModel> simpleViewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
