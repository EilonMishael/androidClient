package com.example.messagingapp.webrtc

import android.app.Application
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer

class WebRtcClient(private val application: Application) {

    internal lateinit var peerConnectionFactory: PeerConnectionFactory
    internal lateinit var eglBase: EglBase

    // Local media stream
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    internal var localAudioTrack: AudioTrack? = null
    var localMediaStream: MediaStream? = null

    // Camera management
    private var videoCapturer: VideoCapturer? = null
    private lateinit var videoTrackRenderer: SurfaceViewRenderer

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        eglBase = EglBase.create() // Simplified EglBase creation

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(application)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/") // Enable H264 high profile for better compatibility
            .createInitializationOptions()

        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    fun startLocalVideo(renderer: SurfaceViewRenderer) {
        this.videoTrackRenderer = renderer
        videoTrackRenderer.init(eglBase.eglBaseContext, null)
        videoTrackRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        videoTrackRenderer.setMirror(true)

        // Create video capturer
        videoCapturer = createCameraCapturer()
        localVideoSource = peerConnectionFactory.createVideoSource(false)

        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, application, localVideoSource?.capturerObserver)
        videoCapturer?.startCapture(1024, 768, 30) // 1024x768 resolution, 30 fps

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", localVideoSource)
        localVideoTrack?.addSink(videoTrackRenderer)
    }

    fun startLocalAudio() {
        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", localAudioSource)
        localAudioTrack?.setEnabled(true)
    }

    fun createLocalMediaStream(): MediaStream {
        localMediaStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localVideoTrack?.let { localMediaStream?.addTrack(it) }
        localAudioTrack?.let { localMediaStream?.addTrack(it) }
        return localMediaStream!!
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(application) // For newer devices, Camera2 is preferred
        val deviceNames = enumerator.deviceNames

        // Try to find a front-facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // If no front-facing camera, try a back-facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null // No camera found
    }

    fun stopLocalVideo() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.removeSink(videoTrackRenderer)
        localVideoTrack?.dispose()
        localVideoSource?.dispose()
        videoTrackRenderer.release()
    }

    fun stopLocalAudio() {
        localAudioTrack?.dispose()
        localAudioSource?.dispose()
    }

    fun dispose() {
        stopLocalVideo()
        stopLocalAudio()
        peerConnectionFactory.dispose()
        eglBase.release()
    }
}

