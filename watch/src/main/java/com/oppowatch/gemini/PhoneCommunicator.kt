package com.oppowatch.gemini

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

object PhoneCommunicator {

    private const val TAG = "PhoneCommunicator"
    const val PATH_TTS = "/gemini_tts_payload"
    const val PATH_ERROR_LOG = "/gemini_error_log"
    const val PATH_REPLY_MESSAGE = "/gemini_reply_message"

    fun sendReplyMessageToPhone(context: Context, recipient: String, message: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send reply message")
                return@addOnSuccessListener
            }
            val payload = org.json.JSONObject().apply {
                put("recipient", recipient)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val bytes = payload.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_REPLY_MESSAGE, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent reply message command to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending reply message command: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes for reply message: ${e.message}")
        }
    }

    fun sendTextToPhone(context: Context, text: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send TTS")
                return@addOnSuccessListener
            }
            val bytes = text.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_TTS, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent TTS payload to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending TTS payload: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes: ${e.message}")
        }
    }

    fun sendErrorLogToPhone(context: Context, errorJson: String) {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)

        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone found to send error log")
                return@addOnSuccessListener
            }
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, PATH_ERROR_LOG, bytes)
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent error log payload to phone node: ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed sending error log payload: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed finding connected nodes for error log: ${e.message}")
        }
    }
}