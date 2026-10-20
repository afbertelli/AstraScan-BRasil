package com.example.serial

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Fila serial FIFO assíncrona para comunicação física com adaptadores ELM327 (v1.5 / v2.1).
 * Comunicação 100% em tempo real com a linha K e barramento do veículo.
 * Controle de concorrência com Mutex e leitura bufferizada até o caractere prompt '>'.
 */
class SerialCommunicationQueue(
    private var socket: BluetoothSocket? = null
) {
    private val mutex = Mutex()
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    init {
        initStreams()
    }

    fun updateSocket(newSocket: BluetoothSocket?) {
        socket = newSocket
        initStreams()
    }

    private fun initStreams() {
        if (socket != null && socket!!.isConnected) {
            try {
                inputStream = socket!!.inputStream
                outputStream = socket!!.outputStream
            } catch (e: Exception) {
                Log.e("SerialQueue", "Erro abrindo streams do socket físico: ${e.message}")
            }
        } else {
            inputStream = null
            outputStream = null
        }
    }

    suspend fun sendCommand(
        command: String,
        timeoutMs: Long = 1800L,
        cloneSafetyDelayMs: Long = 25L
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val currentSocket = socket
            if (currentSocket == null || !currentSocket.isConnected) {
                return@withLock "ERROR: Desconectado"
            }

            try {
                val out = outputStream ?: currentSocket.outputStream.also { outputStream = it }
                val inStream = inputStream ?: currentSocket.inputStream.also { inputStream = it }

                // Esvazia bytes residuais anteriores
                while (inStream.available() > 0) {
                    inStream.read()
                }

                val formattedCmd = command.trim() + "\r"
                out.write(formattedCmd.toByteArray(Charsets.US_ASCII))
                out.flush()

                val responseBuilder = StringBuilder()
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    val available = inStream.available()
                    if (available > 0) {
                        val b = inStream.read()
                        if (b == -1) break
                        val c = b.toChar()
                        if (c == '>') {
                            break
                        }
                        responseBuilder.append(c)
                    } else {
                        delay(6)
                    }
                }

                if (cloneSafetyDelayMs > 0) {
                    delay(cloneSafetyDelayMs)
                }

                val result = responseBuilder.toString().trim()
                return@withLock if (result.isEmpty()) "NO DATA" else result
            } catch (e: Exception) {
                Log.e("SerialQueue", "Falha de I/O em sendCommand '$command': ${e.message}")
                return@withLock "ERROR: ${e.message}"
            }
        }
    }
}
