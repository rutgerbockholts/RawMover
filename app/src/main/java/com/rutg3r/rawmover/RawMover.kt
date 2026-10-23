package com.rutg3r.rawmover

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class RunResult(
    val moved: List<MoveEntry>,
    val failed: List<String>,
    val skippedNotReady: Int,
    val problem: String? = null,
)

/**
 * Finds finished camera videos in the source folder that exceed the duration (or size) threshold
 * and moves them to the target folder with a plain rename (instant, same storage volume).
 */
class RawMover(private val context: Context) {

    private val settings = Settings(context)

    suspend fun processNew(ignoreEnabled: Boolean = false): RunResult {
        return lock.withLock { doRun(ignoreEnabled) }
    }

    private fun doRun(ignoreEnabled: Boolean): RunResult {
        if (!settings.enabled && !ignoreEnabled) return RunResult(emptyList(), emptyList(), 0, "disabled")
        if (!Environment.isExternalStorageManager()) {
            Notifier.permissionMissing(context)
            return RunResult(emptyList(), emptyList(), 0, "All files access not granted")
        }
        val pattern = settings.compiledPattern()
            ?: return RunResult(emptyList(), emptyList(), 0, "Invalid filename pattern")

        val source = settings.sourceRelPath
        val target = settings.targetRelPath
        if (source.isEmpty() || target.isEmpty() || source.equals(target, ignoreCase = true)) {
            return RunResult(emptyList(), emptyList(), 0, "Source and target folders must differ")
        }

        val minDurationMs = settings.minDurationMs
        val sizeRule = settings.sizeRuleEnabled
        val minSizeBytes = settings.minSizeMb.toLong() * 1024 * 1024

        val moved = mutableListOf<MoveEntry>()
        val failed = mutableListOf<String>()
        var notReady = 0

        for (video in queryCandidates(source)) {
            if (!pattern.matches(video.name)) continue

            val file = File(video.path)
            if (!file.isFile || file.length() == 0L) { notReady++; continue }

            val durationMs = video.durationMs?.takeIf { it > 0 } ?: readDuration(video.path)
            val size = file.length()

            val byDuration = durationMs != null && durationMs > minDurationMs
            val bySize = sizeRule && size > minSizeBytes
            if (durationMs == null && !bySize) { notReady++; continue } // index not filled yet; next run

            if (byDuration || bySize) {
                val reason = when {
                    byDuration && bySize -> "length + size"
                    byDuration -> "length"
                    else -> "size"
                }
                val entry = MoveEntry(System.currentTimeMillis(), video.name, durationMs ?: 0, size, reason)
                if (move(file, target)) {
                    MoveLog.add(context, entry)
                    moved += entry
                } else {
                    failed += video.name
                }
            }
        }

        if (moved.isNotEmpty() && settings.notify) Notifier.moved(context, moved, target)
        if (failed.isNotEmpty()) Notifier.failed(context, failed)
        return RunResult(moved, failed, notReady)
    }

    private data class Candidate(val name: String, val path: String, val durationMs: Long?)

    /** Finished videos in the source folder added since [Settings.processSinceSec]. */
    private fun queryCandidates(sourceRelPath: String): List<Candidate> {
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DURATION,
        )
        // RELATIVE_PATH comparison is case-insensitive on purpose ("DCIM/Camera/" vs "DCIM/camera/").
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.MediaColumns.IS_PENDING}=0 AND " +
            "${MediaStore.MediaColumns.DATE_ADDED}>=?"
        val args = arrayOf(sourceRelPath, settings.processSinceSec.toString())
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        val out = mutableListOf<Candidate>()
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, args, sort
            )?.use { c ->
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val durIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx) ?: continue
                    val path = c.getString(dataIdx) ?: continue
                    val dur = if (c.isNull(durIdx)) null else c.getLong(durIdx)
                    out += Candidate(name, path, dur)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media query failed", e)
        }
        return out
    }

    private fun readDuration(path: String): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun move(src: File, targetRelPath: String): Boolean {
        val root = Environment.getExternalStorageDirectory()
        val targetDir = File(root, targetRelPath)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e(TAG, "Cannot create $targetDir")
            return false
        }
        val dst = uniqueTarget(targetDir, src.name)
        val ok = src.renameTo(dst)   // same volume: instant, keeps last-modified time
        if (ok) {
            // Remove the old index row and add the new one so gallery/editors see it right away.
            MediaScannerConnection.scanFile(context, arrayOf(src.absolutePath, dst.absolutePath), null, null)
            Log.i(TAG, "Moved ${src.name} -> $dst")
        } else {
            Log.e(TAG, "Rename failed for $src -> $dst")
        }
        return ok
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "${base}_$i" else "${base}_$i.$ext")
            i++
        }
        return candidate
    }

    companion object {
        private const val TAG = "RawMover"
        private val lock = Mutex()
    }
}
