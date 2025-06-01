package com.example.kokoro82m.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kokoro82m.utils.OnnxRuntimeManager
import kotlinx.coroutines.launch

class MainViewModel(context: Context) : ViewModel() {
    init {
        viewModelScope.launch {
            OnnxRuntimeManager.initialize(context.applicationContext)
        }
    }

    fun getSession() = OnnxRuntimeManager.getSession()
}