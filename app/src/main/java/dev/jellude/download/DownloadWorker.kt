package dev.jellude.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.jellude.JelludeApp
import dev.jellude.R
import dev.jellude.core.TransferFailure
import dev.jellude.core.VerifiedTransfer
import dev.jellude.data.ApiError
import dev.jellude.data.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object { private val slots = Semaphore(3) }
    override suspend fun doWork(): Result = slots.withPermit { withContext(Dispatchers.IO) {
        val app = applicationContext as JelludeApp
        val repo = app.repository ?: return@withContext Result.failure()
        if (repo.session.namespace != inputData.getString("namespace")) return@withContext Result.failure()
        if (app.store.offline) return@withContext Result.retry()
        val id = inputData.getString("track") ?: return@withContext Result.failure()
        if (repo.dao.members().none { it.trackId == id }) return@withContext Result.success()
        val item = repo.dao.item(id) ?: return@withContext Result.failure()
        val previous = repo.dao.asset(id)
        val hasPrevious = previous?.state == "Ready" && repo.file(id).exists() && repo.file(id).length() == previous.bytes
        try {
            setForeground(notification(item.title, id))
            repo.dao.putAsset((previous ?: Asset(id)).copy(state = if (hasPrevious) "Ready" else "Downloading", transferState = "Downloading", error = null))
            val downloaded = VerifiedTransfer(app.http).download(repo.api.request("Items/$id/Download").build(), repo.partial(id)) {
                isStopped || app.store.offline
            }
            repo.dao.putAsset(if (hasPrevious) previous!!.copy(transferState = "Verifying") else Asset(id, "Verifying", downloaded.bytes))
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(repo.partial(id).absolutePath)
                check((0 until extractor.trackCount).any {
                    extractor.getTrackFormat(it).getString("mime")?.startsWith("audio/") == true
                }) { "Downloaded file has no readable audio track" }
            } finally { extractor.release() }
            Files.move(repo.partial(id).toPath(), repo.file(id).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            File(repo.partial(id).path + ".etag").delete()
            repo.dao.putAsset(Asset(id, "Ready", downloaded.bytes, downloaded.sha256,
                verifiedAt = System.currentTimeMillis(), sourceTag = item.sourceTag))
            try { repo.refreshLyrics(id) } catch (_: Exception) { /* Lyrics do not block playable audio. */ }
            try { repo.cacheArtwork(id) } catch (_: Exception) { /* Artwork does not block playable audio. */ }
            Result.success()
        } catch (e: Exception) {
            val status = (e as? TransferFailure)?.status ?: (e as? ApiError)?.status
            if (status == 416) { repo.partial(id).delete(); File(repo.partial(id).path + ".etag").delete() }
            val retry = status !in listOf(401, 403, 404) && runAttemptCount < 5
            // Never expose exception text that might include authenticated URLs.
            val reason = when (status) {
                401 -> "Sign in again to continue"
                403 -> "Download permission denied"
                404 -> "Track unavailable on server"
                else -> if (app.store.offline) "Waiting: offline mode" else "Transfer or validation failed. Retry or repair."
            }
            repo.dao.putAsset((repo.dao.asset(id) ?: Asset(id)).copy(
                state = if (hasPrevious) "Ready" else if (retry) "Waiting to retry" else "Needs attention",
                transferState = if (retry) "Waiting to retry" else "Needs attention", error = reason))
            if (retry) Result.retry() else Result.failure()
        }
    } }
    private fun notification(title: String, id: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", "Music downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(R.drawable.ic_stat_jellude).setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setContentText(title).setOngoing(true).build()
        return ForegroundInfo(id.hashCode() and Int.MAX_VALUE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as JelludeApp
        val repo = app.repository ?: return Result.failure()
        if (repo.session.namespace != inputData.getString("namespace")) return Result.failure()
        if (app.store.offline) return Result.success()
        return try { repo.sync(); Result.success() } catch (_: Exception) { Result.retry() }
    }
}
