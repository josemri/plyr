package com.plyr.utils

object SpotifyAuthEvent {
    private var callback: ((Boolean, String?) -> Unit)? = null
    
    fun setAuthCallback(callback: (Boolean, String?) -> Unit) {
        this.callback = callback
    }
    
    fun onAuthComplete(success: Boolean, message: String? = null) {
        callback?.invoke(success, message)
    }
    
    fun clearCallback() {
        callback = null
    }
}
