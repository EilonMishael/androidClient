package com.example.messagingapp.webrtc

interface SignalingClient {
    fun connect(serverAddress: String)
    fun send(message: String)
    fun disconnect()

    interface SignalingEvents {
        fun onConnectionEstablished()
        fun onMessageReceived(message: String)
        fun onConnectionClosed()
        fun onConnectionError(errorMessage: String)
    }

    fun setSignalingEventsListener(listener: SignalingEvents)
}
