package com.example.messagingapp.webrtc

import java.util.Collections

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * A temporary signaling client that uses a static IP and port for peer-to-peer communication.
 * This is a placeholder for a real signaling server (e.g., WebSocket) and should be replaced in a production environment.
 * It simulates offer/answer/ICE candidate exchange over a simple TCP socket.
 */
class StaticIpSignalingClient : SignalingClient {

    private var signalingEventsListener: SignalingClient.SignalingEvents? = null
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var listenJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Channel to safely send messages to the socket from different coroutines
    private val outgoingMessages = Channel<String>(Channel.UNLIMITED)

    /**
     * Connects to a peer at the given server address (IP:Port).
     * If the connection fails, it attempts to start a server socket to listen for incoming connections.
     */
    override fun connect(serverAddress: String) {
        scope.launch {
            try {
                val (ip, port) = serverAddress.split(":").let { it[0] to it[1].toInt() }

                // Try to connect as a client first
                socket = Socket(ip, port)
                setupStreams(socket!!)
                signalingEventsListener?.onConnectionEstablished()
                startListeningForMessages()
                startSendingOutgoingMessages()

            } catch (e: Exception) {
                // If client connection fails, try to act as a server
                disconnect() // Ensure any previous connections are closed
                startServer(serverAddress)
            }
        }
    }

    /**
     * Starts a server socket to listen for incoming connections on the specified port.
     */
    private fun startServer(serverAddress: String) {
        scope.launch {
            try {
                val (ip, port) = serverAddress.split(":").let { it[0] to it[1].toInt() }
                serverSocket = ServerSocket(port)
                signalingEventsListener?.onMessageReceived("Listening on " + getLocalIpAddress() + ":" + port)
                val clientSocket = serverSocket?.accept() // Blocks until a client connects
                if (clientSocket != null) {
                    socket = clientSocket
                    setupStreams(socket!!)
                    signalingEventsListener?.onConnectionEstablished()
                    startListeningForMessages()
                    startSendingOutgoingMessages()
                }
            } catch (e: Exception) {
                signalingEventsListener?.onConnectionError("Server error: ${e.message}")
                disconnect()
            }
        }
    }

    private fun setupStreams(s: Socket) {
        reader = BufferedReader(InputStreamReader(s.getInputStream()))
        writer = PrintWriter(s.getOutputStream(), true)
    }

    /**
     * Sends a message to the connected peer.
     */
    override fun send(message: String) {
        scope.launch {
            outgoingMessages.send(message)
        }
    }

    private fun startSendingOutgoingMessages() {
        scope.launch {
            for (message in outgoingMessages) {
                writer?.println(message)
            }
        }
    }

    /**
     * Starts a background job to listen for incoming messages from the peer.
     */
    private fun startListeningForMessages() {
        listenJob = scope.launch {
            try {
                while (isActive) {
                    val message = reader?.readLine()
                    if (message != null) {
                        signalingEventsListener?.onMessageReceived(message)
                    } else {
                        // Peer closed the connection
                        signalingEventsListener?.onConnectionClosed()
                        disconnect()
                        break
                    }
                }
            } catch (e: Exception) {
                if (isActive) { // Only report error if job was not cancelled intentionally
                    signalingEventsListener?.onConnectionError("Read error: ${e.message}")
                }
                disconnect()
            }
        }
    }

    /**
     * Disconnects from the peer and cleans up resources.
     */
    override fun disconnect() {
        scope.launch {
            listenJob?.cancel()
            outgoingMessages.close()
            socket?.close()
            serverSocket?.close()
            reader?.close()
            writer?.close()
            socket = null
            serverSocket = null
            reader = null
            writer = null
            signalingEventsListener?.onConnectionClosed()
        }
    }

    override fun setSignalingEventsListener(listener: SignalingClient.SignalingEvents) {
        this.signalingEventsListener = listener
    }

    // Helper function to get local IP address (duplicated from MainActivity for simplicity in this temp client)
    private fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
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
}

