package com.example.messagingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.messagingapp.databinding.ActivityCallBinding
import com.example.messagingapp.webrtc.CallController
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer

/**
 * CallActivity is responsible for displaying the in-call UI and interacting with the CallController.
 * It handles local and remote video rendering, and provides controls for muting, switching camera, and ending the call.
 */
class CallActivity : AppCompatActivity() {

    private val TAG = "CallActivity"

    private lateinit var binding: ActivityCallBinding
    private lateinit var callController: CallController
    private var isAudioOnly: Boolean = false
    private var peerAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve call parameters from the intent
        peerAddress = intent.getStringExtra("peerAddress")
        isAudioOnly = intent.getBooleanExtra("isVideoCall", false).not() // true if isAudioCall

        if (peerAddress == null) {
            Log.e(TAG, "Peer address not provided.")
            Toast.makeText(this, "Error: Peer address missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize CallController
        callController = CallController(application)

        // Set up CallController callbacks
        callController.onRemoteStreamAdded = { remoteStream ->
            // Remote stream added, now can render remote video
            Log.d(TAG, "Remote stream added. Video tracks: ${remoteStream.videoTracks.size}")
            // Note: remoteVideoTrack is already added to remoteRenderer in CallController.onAddStream
            if (remoteStream.videoTracks.isEmpty() && !isAudioOnly) {
                // If video call but no remote video track, show audio only indicator if not already visible
                runOnUiThread { binding.audioOnlyIndicator.visibility = View.VISIBLE }
            }
        }

        callController.onCallEnded = { runOnUiThread { finish() } }

        callController.onConnectionError = { errorMessage ->
            runOnUiThread {
                Toast.makeText(this, "Call error: $errorMessage", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // Initialize SurfaceViewRenderers
        binding.localVideoView.setMirror(true)
        binding.localVideoView.init(callController.getEglBaseContext(), null)
        binding.remoteVideoView.init(callController.getEglBaseContext(), null)

        if (isAudioOnly) {
            binding.localVideoView.visibility = View.GONE
            binding.remoteVideoView.visibility = View.GONE
            binding.audioOnlyIndicator.visibility = View.VISIBLE
            callController.startAudioCall(peerAddress!!, binding.localVideoView, binding.remoteVideoView)
        } else {
            binding.audioOnlyIndicator.visibility = View.GONE
            binding.localVideoView.visibility = View.VISIBLE
            binding.remoteVideoView.visibility = View.VISIBLE
            callController.startVideoCall(peerAddress!!, binding.localVideoView, binding.remoteVideoView)
        }

        // Set up UI control buttons
        binding.muteButton.setOnClickListener {
            if (callController.isMuted) {
                callController.unmute()
                binding.muteButton.setImageResource(android.R.drawable.ic_lock_silent_mode)
                Toast.makeText(this, "Mic unmuted", Toast.LENGTH_SHORT).show()
            } else {
                callController.mute()
                binding.muteButton.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                Toast.makeText(this, "Mic muted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchCameraButton.setOnClickListener {
            callController.switchCamera()
            Toast.makeText(this, "Switching camera...", Toast.LENGTH_SHORT).show()
        }

        binding.endCallButton.setOnClickListener {
            callController.endCall()
        }
    }

    override fun onDestroy() {
        callController.endCall() // Ensure resources are released if activity is destroyed prematurely
        binding.localVideoView.release()
        binding.remoteVideoView.release()
        super.onDestroy()
    }

    // Removed these functions as CallController now directly manages renderers
    // fun getLocalVideoView(): SurfaceViewRenderer = binding.localVideoView
    // fun getRemoteVideoView(): SurfaceViewRenderer = binding.remoteVideoView
}