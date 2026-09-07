package com.oppowatch.gemini.phone

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

class QuickReplyNotificationService : NotificationListenerService() {

    data class CachedQuickReply(
        val key: String,
        val packageName: String,
        val appName: String,
        val senderName: String,
        val action: Notification.Action,
        val remoteInput: RemoteInput,
        val postTime: Long
    )

    data class ReplyResult(
        val success: Boolean,
        val senderName: String,
        val appName: String,
        val replyText: String,
        val errorMessage: String = ""
    )

    companion object {
        private const val TAG = "QuickReplyService"

        @Volatile
        private var instance: QuickReplyNotificationService? = null

        // Danh sách các ứng dụng nhắn tin phổ biến được hỗ trợ
        private val SUPPORTED_PACKAGES = mapOf(
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger Lite",
            "com.zing.zalo" to "Zalo",
            "org.telegram.messenger" to "Telegram",
            "org.telegram.messenger.web" to "Telegram Web",
            "org.thunderdog.challegram" to "Telegram X",
            "com.google.android.apps.messaging" to "Tin nhắn",
            "com.samsung.android.messaging" to "Tin nhắn",
            "com.android.mms" to "Tin nhắn SMS",
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business"
        )

        @Volatile
        private var lastReceivedReply: CachedQuickReply? = null
        private val recentRepliesMap = Collections.synchronizedMap(LinkedHashMap<String, CachedQuickReply>(30))

        fun isNotificationAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun getLastSenderName(): String? {
            return lastReceivedReply?.senderName
        }

        fun getRecentRepliesCount(): Int {
            return recentRepliesMap.size
        }

        /**
         * Gửi tin nhắn phản hồi ngầm qua RemoteInput
         */
        fun sendReply(context: Context, targetName: String?, replyText: String): ReplyResult {
            val candidate = findTargetReply(targetName)
                ?: return ReplyResult(
                    success = false,
                    senderName = targetName ?: "Gần nhất",
                    appName = "",
                    replyText = replyText,
                    errorMessage = if (targetName.isNullOrBlank()) {
                        "Không tìm thấy tin nhắn gần đây để trả lời."
                    } else {
                        "Không tìm thấy tin nhắn gần đây của $targetName."
                    }
                )

            return try {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putCharSequence(candidate.remoteInput.resultKey, replyText)
                RemoteInput.addResultsToIntent(arrayOf(candidate.remoteInput), intent, bundle)

                // Kích hoạt PendingIntent của Action
                candidate.action.actionIntent.send(context, 0, intent)
                Log.i(TAG, "✓ Đã gửi ngầm trả lời tới [${candidate.senderName}] qua [${candidate.appName}]: $replyText")

                ReplyResult(
                    success = true,
                    senderName = candidate.senderName,
                    appName = candidate.appName,
                    replyText = replyText
                )
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi kích hoạt PendingIntent trả lời: ${e.message}", e)
                ReplyResult(
                    success = false,
                    senderName = candidate.senderName,
                    appName = candidate.appName,
                    replyText = replyText,
                    errorMessage = "Lỗi hệ thống khi gửi: ${e.localizedMessage}"
                )
            }
        }

        private fun findTargetReply(targetName: String?): CachedQuickReply? {
            // Thử đồng bộ lại từ activeNotifications nếu có service instance
            try {
                instance?.let { srv ->
                    val active = srv.activeNotifications
                    if (active != null) {
                        for (sbn in active) {
                            srv.processNotification(sbn)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Không thể quét activeNotifications: ${e.message}")
            }

            if (targetName.isNullOrBlank()) {
                return lastReceivedReply ?: recentRepliesMap.values.lastOrNull()
            }

            val cleanQuery = removeAccents(targetName.trim().lowercase(Locale.getDefault()))

            // 1. Tìm khớp chính xác hoặc chứa chuỗi trong cache
            synchronized(recentRepliesMap) {
                // Ưu tiên tin mới nhất trước
                val list = recentRepliesMap.values.toList().reversed()
                for (item in list) {
                    val cleanSender = removeAccents(item.senderName.lowercase(Locale.getDefault()))
                    if (cleanSender == cleanQuery || cleanSender.contains(cleanQuery) || cleanQuery.contains(cleanSender)) {
                        return item
                    }
                }
            }

            // 2. Fallback: Nếu chỉ có đúng 1 tin nhắn gần nhất trong cache, dùng luôn tin đó
            return lastReceivedReply
        }

        private fun removeAccents(str: String): String {
            val nfd = Normalizer.normalize(str, Normalizer.Form.NFD)
            val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
            return pattern.matcher(nfd).replaceAll("").replace("đ", "d").replace("Đ", "D")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "QuickReplyNotificationService connected to system.")
        try {
            val active = activeNotifications
            if (active != null) {
                for (sbn in active) {
                    processNotification(sbn)
                }
                Log.i(TAG, "onListenerConnected: Đã nạp ${active.size} thông báo vào cache")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi quét activeNotifications trong onListenerConnected: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "QuickReplyNotificationService disconnected from system.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        processNotification(sbn)
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        // Kiểm tra ứng dụng thuộc whitelist hoặc có danh mục Tin nhắn
        val isSupportedApp = SUPPORTED_PACKAGES.containsKey(pkg)
        val isMessageCategory = notification.category == Notification.CATEGORY_MESSAGE

        if (!isSupportedApp && !isMessageCategory) return

        val appName = SUPPORTED_PACKAGES[pkg] ?: getAppNameFromPackage(pkg)

        // Trích xuất cổng trả lời nhanh (RemoteInput)
        val quickReplyAction = findQuickReplyAction(notification) ?: return
        val senderName = extractSenderName(notification) ?: appName

        val cachedItem = CachedQuickReply(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            senderName = senderName,
            action = quickReplyAction.first,
            remoteInput = quickReplyAction.second,
            postTime = sbn.postTime
        )

        lastReceivedReply = cachedItem
        recentRepliesMap[senderName.lowercase(Locale.getDefault())] = cachedItem
        Log.d(TAG, "Đã lưu cổng trả lời nhanh cho [$senderName] ($appName), tổng cache: ${recentRepliesMap.size}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        // Xóa thông báo tương ứng khỏi cache nếu người dùng đã quẹt bỏ
        synchronized(recentRepliesMap) {
            val iterator = recentRepliesMap.values.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.key == sbn.key) {
                    iterator.remove()
                    break
                }
            }
        }
        if (lastReceivedReply?.key == sbn.key) {
            lastReceivedReply = recentRepliesMap.values.lastOrNull()
        }
    }

    private fun findQuickReplyAction(notification: Notification): Pair<Notification.Action, RemoteInput>? {
        // 1. Duyệt actions thông thường
        val actions = notification.actions
        if (actions != null) {
            for (action in actions) {
                val input = findFreeFormRemoteInput(action)
                if (input != null) {
                    return Pair(action, input)
                }
            }
        }

        // 2. Duyệt invisible actions (tương thích các bản Messenger, Zalo, Android Auto)
        val invisibleActions = NotificationCompat.getInvisibleActions(notification)
        for (compatAction in invisibleActions) {
            val inputs = compatAction.remoteInputs
            if (inputs != null) {
                for (input in inputs) {
                    if (input.allowFreeFormInput) {
                        // Chuyển đổi sang Notification.Action
                        val nativeAction = Notification.Action.Builder(
                            compatAction.icon,
                            compatAction.title,
                            compatAction.actionIntent
                        ).addRemoteInput(
                            RemoteInput.Builder(input.resultKey)
                                .setLabel(input.label)
                                .setChoices(input.choices)
                                .setAllowFreeFormInput(input.allowFreeFormInput)
                                .addExtras(input.extras)
                                .build()
                        ).build()

                        val nativeInput = nativeAction.remoteInputs?.firstOrNull()
                        if (nativeInput != null) {
                            return Pair(nativeAction, nativeInput)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findFreeFormRemoteInput(action: Notification.Action): RemoteInput? {
        val inputs = action.remoteInputs ?: return null
        for (input in inputs) {
            if (input.allowFreeFormInput) {
                return input
            }
        }
        return null
    }

    private fun extractSenderName(notification: Notification): String? {
        val extras = notification.extras ?: return null
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        if (!convTitle.isNullOrEmpty()) return convTitle

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        if (!title.isNullOrEmpty()) return title

        val bigTitle = extras.getCharSequence(NotificationCompat.EXTRA_TITLE_BIG)?.toString()?.trim()
        if (!bigTitle.isNullOrEmpty()) return bigTitle

        return null
    }

    private fun getAppNameFromPackage(pkg: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast(".")
        }
    }
}
