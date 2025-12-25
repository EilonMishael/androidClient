package com.example.messagingapp.models

import kotlinx.serialization.Serializable

/**
 * Represents various signaling commands exchanged between peers.
 * Uses Kotlinx Serialization for easy conversion to/from JSON.
 */
@Serializable
data class SignalingCommand(
    val type: String, // e.g., "offer", "answer", "candidate"
    val sdp: String? = null, // For offer/answer
    val sdpMid: String? = null, // For ICE candidates
    val sdpMLineIndex: Int? = null, // For ICE candidates
    val sdpCandidate: String? = null // For ICE candidates
)

