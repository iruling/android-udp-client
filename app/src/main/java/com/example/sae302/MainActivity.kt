package com.example.sae302

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sae302.ui.theme.Sae302Theme
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val udpClient = UdpClient { message ->
        runOnUiThread { receivedMessages.add(getString(R.string.log_received, message)) }
    }
    private val receivedMessages = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        udpClient.start()
        setContent {
            Sae302Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UdpClientScreen(
                        messages = receivedMessages,
                        onSendClick = { message ->
                            receivedMessages.add(getString(R.string.log_sent, message))
                            udpClient.sendMessage(message)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        udpClient.stop()
        super.onDestroy()
    }
}

@Composable
fun UdpClientScreen(
    messages: List<String>,
    onSendClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(id = R.string.message_label)) }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendClick(text)
                        text = ""
                    }
                }
            ) {
                Text(stringResource(id = R.string.send_button))
            }
        }
        Text(
            text = stringResource(id = R.string.log_title),
            style = MaterialTheme.typography.titleMedium
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp)
                .verticalScroll(scrollState)
        ) {
            messages.forEach { message ->
                Text(text = message, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UdpClientScreenPreview() {
    Sae302Theme {
        UdpClientScreen(
            messages = listOf("Envoyé: aaaa", "Reçu: BBB,AAA,CC"),
            onSendClick = {}
        )
    }
}

private class UdpClient(
    private val onMessageReceived: (String) -> Unit
) {
    private val serverAddress = InetAddress.getLoopbackAddress()
    @Volatile private var socket: DatagramSocket? = null
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val receiveExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile private var running = false

    fun start() {
        if (running) return
        receiveExecutor.execute {
            try {
                socket = DatagramSocket().apply { soTimeout = 1000 }
                running = true
            } catch (exception: Exception) {
                Log.e(TAG, "Impossible d'initialiser le socket UDP", exception)
                onMessageReceived("Erreur réception UDP")
                return@execute
            }

            while (running && socket != null) {
                try {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    onMessageReceived(message)
                } catch (_: SocketTimeoutException) {
                    // Continue to check running flag.
                } catch (exception: Exception) {
                    Log.e(TAG, "Erreur lors de la réception UDP", exception)
                    if (running) {
                        onMessageReceived("Erreur réception UDP")
                    }
                }
            }
        }
    }

    fun sendMessage(message: String) {
        sendExecutor.execute {
            try {
                val localSocket = socket ?: return@execute
                if (!running) return@execute
                val bytes = message.toByteArray()
                val packet = DatagramPacket(bytes, bytes.size, serverAddress, UDP_PORT)
                localSocket.send(packet)
            } catch (exception: Exception) {
                Log.e(TAG, "Erreur lors de l'envoi UDP", exception)
                onMessageReceived("Erreur envoi UDP")
            }
        }
    }

    fun stop() {
        running = false
        sendExecutor.shutdownNow()
        receiveExecutor.shutdownNow()
        socket?.close()
        socket = null
    }

    private companion object {
        const val TAG = "UdpClient"
        const val UDP_PORT = 6010
    }
}