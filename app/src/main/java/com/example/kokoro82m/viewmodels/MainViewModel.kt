// Adopted from: https://github.com/puff-dayo/Kokoro-82M-Android

package com.example.kokoro82m.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kokoro82m.tts.inference.OnnxRuntimeManager
import kotlinx.coroutines.launch

class MainViewModel(context: Context) : ViewModel() {
    init {
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(context.applicationContext)
        }
    }

    fun getSession() = OnnxRuntimeManager.getSession()
}