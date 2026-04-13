package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel for Splash Screen initialization.
 * Manages app startup state and preloads necessary data.
 */
@HiltViewModel
class SplashViewModel @Inject constructor() : ViewModel() {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // Immediately mark as initialized - no artificial delay needed
        _isInitialized.update { true }
    }
}
