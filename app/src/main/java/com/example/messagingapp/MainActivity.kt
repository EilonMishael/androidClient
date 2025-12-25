package com.example.messagingapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.messagingapp.databinding.ActivityMainBinding
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
            // Permissions are granted, proceed with app logic (e.g., enable call buttons)
        } else {
            Toast.makeText(this, "Permissions denied. Cannot make calls.", Toast.LENGTH_LONG).show()
            // Handle denial, e.g., disable call buttons or show a dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()

        // Display local IP address
        binding.myIpAddress.text = getLocalIpAddress() + ":8080" // Placeholder port

        binding.videoCallButton.setOnClickListener {
            if (hasPermissions()) {
                val peerAddress = binding.peerIpEditText.text.toString()
                if (peerAddress.isNotBlank()) {
                    startCallActivity(peerAddress, isVideoCall = true)
                } else {
                    Toast.makeText(this, "Please enter Peer IP:Port", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.audioCallButton.setOnClickListener {
            if (hasPermissions()) {
                val peerAddress = binding.peerIpEditText.text.toString()
                if (peerAddress.isNotBlank()) {
                    startCallActivity(peerAddress, isVideoCall = false)
                } else {
                    Toast.makeText(this, "Please enter Peer IP:Port", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET // INTERNET is usually granted by default, but good to include for completeness for runtime check on other permissions
        )
        if (!hasPermissions(*permissions)) {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun hasPermissions(vararg permissions: String): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                val inetAddresses = Collections.list(networkInterface.getInetAddresses())
                for (inetAddress in inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && inetAddress.isSiteLocalAddress) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "N/A"
    }

    private fun startCallActivity(peerAddress: String, isVideoCall: Boolean) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("peerAddress", peerAddress)
            putExtra("isVideoCall", isVideoCall)
        }
        startActivity(intent)
    }
}