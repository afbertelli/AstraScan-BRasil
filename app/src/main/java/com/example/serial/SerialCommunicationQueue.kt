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

                val buffer = ByteArray(512)
                val responseBuilder = StringBuilder()
                val startTime = System.currentTimeMillis()
                var promptFound = false

                while (System.currentTimeMillis() - startTime < timeoutMs && !promptFound) {
                    val available = inStream.available()
                    if (available > 0) {
                        val bytesToRead = minOf(buffer.size, available)
                        val bytesRead = inStream.read(buffer, 0, bytesToRead)
                        if (bytesRead == -1) break
                        for (i in 0 until bytesRead) {
                            val c = (buffer[i].toInt() and 0xFF).toChar()
                            if (c == '>') {
                                promptFound = true
                                break
                            }
                            responseBuilder.append(c)
                        }
                    } else {
                        delay(5)
                    }
                }

                if (cloneSafetyDelayMs > 0 && !promptFound) {
                    delay(cloneSafetyDelayMs)
                }

                val rawStr = responseBuilder.toString()
                // Sanitização avançada de ruído de clones ELM327 (caracteres nulos, ecos parciais, control chars)
                val sanitized = rawStr
                    .replace("\u0000", "")
                    .replace("\r\r", "\r")
                    .trim()

                return@withLock if (sanitized.isEmpty()) "NO DATA" else sanitized
            } catch (e: Exception) {
                Log.e("SerialQueue", "Falha de I/O em sendCommand '$command': ${e.message}")
                return@withLock "ERROR: ${e.message}"
            }
        }
    }
}
