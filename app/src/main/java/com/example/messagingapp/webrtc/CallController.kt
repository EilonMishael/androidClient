package com.example.messagingapp.webrtc

import android.app.Application
import android.util.Log
import com.example.messagingapp.models.SignalingCommand
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory.Options

/**
 * Manages the WebRTC peer connection and call flow.
 * This class handles the creation and management of PeerConnection, local and remote media streams,
 * and interacts with the SignalingClient for exchanging signaling messages.
 *
 * It follows an MVC-ish structure where it acts as the controller, coordinating between the UI
 * (Activities/Fragments) and the underlying WebRTC and signaling components.
 */
class CallController(private val application: Application) : PeerConnection.Observer, SdpObserver,
    SignalingClient.SignalingEvents {

    private val TAG = "CallController"

    private lateinit var webRtcClient: WebRtcClient
    private lateinit var signalingClient: SignalingClient
    private var peerConnection: PeerConnection? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    // Expose EGL context for SurfaceViewRenderer initialization in UI
    fun getEglBaseContext(): EglBase.Context {
        return webRtcClient.eglBase.eglBaseContext
    }

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    // Call state management
    private var isInitiator: Boolean = false
    private var isAudioOnlyCall: Boolean = false
    var isMuted: Boolean = false

    // Callbacks to the UI (e.g., CallActivity)
    var onRemoteStreamAdded: ((MediaStream) -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null
    var onConnectionError: ((String) -> Unit)? = null

    init {
        webRtcClient = WebRtcClient(application)
        signalingClient = StaticIpSignalingClient() // Temporary static IP signaling
        signalingClient.setSignalingEventsListener(this)
    }

    fun startVideoCall(peerAddress: String, localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        isAudioOnlyCall = false
        startCall(peerAddress, localRenderer, remoteRenderer)
    }

    fun startAudioCall(peerAddress: String, localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        isAudioOnlyCall = true
        startCall(peerAddress, localRenderer, remoteRenderer)
    }

    private fun startCall(peerAddress: String, localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        this.localRenderer = localRenderer
        this.remoteRenderer = remoteRenderer

        // Initialize WebRTC client for video if not audio-only
        if (!isAudioOnlyCall) {
            webRtcClient.startLocalVideo(localRenderer)
        }
        webRtcClient.startLocalAudio()

        // Connect to signaling server
        signalingClient.connect(peerAddress)
    }

    // This is called when the signaling connection is established (SignalingClient.SignalingEvents callback)
    override fun onConnectionEstablished() {
        Log.d(TAG, "Signaling connection established.")
        // We are the initiator if we successfully connected to the peer directly
        isInitiator = true
        createPeerConnection()
        if (isInitiator) {
            createOffer()
        }
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            // Google STUN server (free and public)
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Other configurations can be added here (e.g., ICE transport policy)
        }

        val peerConnectionFactory = webRtcClient.peerConnectionFactory
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this)

        // Add local media stream to the peer connection
        val localStream = webRtcClient.createLocalMediaStream()
        peerConnection?.addStream(localStream)

        // Enable/disable video track based on call type
        localStream.videoTracks.firstOrNull()?.setEnabled(!isAudioOnlyCall)
    }

    private fun createOffer() {
        val sdpConstraints = MediaConstraints().apply {
            if (!isAudioOnlyCall) {
                // Offer to send/receive video if it's a video call
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("VoiceActivityDetection", "true"))
        }
        peerConnection?.createOffer(this, sdpConstraints)
    }

    private fun createAnswer() {
        val sdpConstraints = MediaConstraints().apply {
            if (!isAudioOnlyCall) {
                // Answer to send/receive video if it's a video call
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("VoiceActivityDetection", "true"))
        }
        peerConnection?.createAnswer(this, sdpConstraints)
    }

    fun endCall() {
        peerConnection?.close()
        webRtcClient.dispose()
        signalingClient.disconnect()
        onCallEnded?.invoke()
        Log.d(TAG, "Call ended.")
    }

    fun mute() {
        webRtcClient.localAudioTrack?.setEnabled(false)
        isMuted = true
        Log.d(TAG, "Microphone muted.")
    }

    fun unmute() {
        webRtcClient.localAudioTrack?.setEnabled(true)
        isMuted = false
        Log.d(TAG, "Microphone unmuted.")
    }

    fun switchCamera() {
        // TODO: Implement camera switching logic in WebRtcClient
        Log.d(TAG, "Switch camera not yet implemented.")
    }

    // region PeerConnection.Observer callbacks
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
        Log.d(TAG, "onSignalingChange: $state")
    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
        Log.d(TAG, "onIceConnectionChange: $state")
        if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.CLOSED) {
            Log.d(TAG, "ICE Connection disconnected or closed.")
            endCall()
        }
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "onIceGatheringChange: $state")
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        Log.d(TAG, "onIceCandidate: $candidate")
        candidate?.let {
            // Send ICE candidate to remote peer via signaling
            val command = SignalingCommand(
                type = "candidate",
                sdpMid = it.sdpMid,
                sdpMLineIndex = it.sdpMLineIndex,
                sdpCandidate = it.sdp
            )
            signalingClient.send(Json.encodeToString(command))
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Log.d(TAG, "onIceCandidatesRemoved")
    }

    override fun onAddStream(stream: MediaStream?) {
        Log.d(TAG, "onAddStream: ${stream?.id}")
        stream?.let {
            if (it.videoTracks.size > 0) {
                remoteVideoTrack = it.videoTracks.first()
                remoteVideoTrack?.addSink(remoteRenderer)
            }
            if (it.audioTracks.size > 0) {
                remoteAudioTrack = it.audioTracks.first()
                remoteAudioTrack?.setEnabled(true)
            }
            onRemoteStreamAdded?.invoke(it)
        }
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Log.d(TAG, "onRemoveStream")
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.d(TAG, "onDataChannel")
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded")
    }

    override fun onAddTrack(rtpReceiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        Log.d(TAG, "onAddTrack")
    }
    // endregion PeerConnection.Observer callbacks

    // region SdpObserver callbacks
    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        Log.d(TAG, "onCreateSuccess: ${sessionDescription?.type}")
        peerConnection?.setLocalDescription(this, sessionDescription)
        sessionDescription?.let {
            // Send offer/answer to remote peer via signaling
            val command = SignalingCommand(
                type = it.type.canonicalForm(),
                sdp = it.description
            )
            signalingClient.send(Json.encodeToString(command))
        }
    }

    override fun onSetSuccess() {
        Log.d(TAG, "onSetSuccess")
        if (!isInitiator && peerConnection?.localDescription?.type == SessionDescription.Type.OFFER) {
            // We just set the remote offer, now create an answer
            createAnswer()
        } else if (isInitiator && peerConnection?.remoteDescription?.type == SessionDescription.Type.ANSWER) {
            // We just set the remote answer, call is established
            Log.d(TAG, "Call established with remote answer.")
        }
    }

    override fun onCreateFailure(error: String?) {
        Log.e(TAG, "onCreateFailure: $error")
        onConnectionError?.invoke("SDP creation failed: $error")
    }

    override fun onSetFailure(error: String?) {
        Log.e(TAG, "onSetFailure: $error")
        onConnectionError?.invoke("SDP setting failed: $error")
    }
    // endregion SdpObserver callbacks

    // region SignalingClient.SignalingEvents callbacks
    override fun onMessageReceived(message: String) {
        Log.d(TAG, "Signaling message received: $message")
        // Parse signaling message and act accordingly
        val command = Json.decodeFromString<SignalingCommand>(message)
        when (command.type) {
            "offer" -> {
                if (peerConnection == null) {
                    // If we receive an offer and no peer connection exists, it means we are the answerer
                    isInitiator = false
                    createPeerConnection()
                }
                peerConnection?.setRemoteDescription(this, SessionDescription(SessionDescription.Type.OFFER, command.sdp))
            }
            "answer" -> {
                peerConnection?.setRemoteDescription(this, SessionDescription(SessionDescription.Type.ANSWER, command.sdp))
            }
            "candidate" -> {
                command.sdpCandidate?.let {
                    val iceCandidate = IceCandidate(it, command.sdpMLineIndex ?: 0, command.sdpMid ?: "")
                    peerConnection?.addIceCandidate(iceCandidate)
                }
            }
            else -> Log.w(TAG, "Unknown signaling command type: ${command.type}")
        }
    }

    override fun onConnectionClosed() {
        Log.d(TAG, "Signaling connection closed.")
        endCall()
    }

    override fun onConnectionError(errorMessage: String) {
        Log.e(TAG, "Signaling connection error: $errorMessage")
        onConnectionError?.invoke(errorMessage)
        endCall()
    }
    // endregion SignalingClient.SignalingEvents callbacks
}
