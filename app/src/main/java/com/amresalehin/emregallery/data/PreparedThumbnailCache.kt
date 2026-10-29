package com.amresalehin.emregallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * High-performance thumbnail cache for Emre Gallery.
 *
 * Implements the Space-Time Tradeoff & Smooth UI Performance architecture:
 * 1. Exact-Fit In-Memory LruCache: Decoded hardware bitmaps reside in RAM (using largeHeap headroom),
 *    delivering instantaneous 0ms hits during scroll without decompressing JPEGs/WebPs on every frame.
 * 2. Hardware Bitmaps (Bitmap.Config.HARDWARE): Decoded pixels are uploaded directly into GPU graphic
 *    buffers, eliminating Dalvik/ART garbage collection pauses entirely.
 * 3. Non-Blocking Asynchronous Disk Cache: ContentResolver/MediaStore loads are cached in memory and
 *    returned immediately; WebP disk persistence runs asynchronously without stalling thumbnail delivery.
 * 4. Scroll-Aware Prewarming: The background prewarmer yields instantly during active user flings/scrolls,
 *    reserving 100% of CPU and I/O bandwidth for visible viewport rendering.
 */
object PreparedThumbnailCache {
    private const val DIRECTORY_NAME = "prepared_thumbnails"
    private const val FREE_SPACE_RESERVE_BYTES = 768L * 1024L * 1024L
    private const val WEBP_QUALITY = 80
    private const val PREWARM_WIDTH = 420
    private const val PREWARM_HEIGHT = 420

    // Fixed stripes avoid retaining one Mutex and URI-sized key per photo for large libraries.
    private val uriLocks = Array(256) { Mutex() }

    private val diskWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Dynamic RAM cache size: Allocate 35% of max JVM heap (typically 128MB-256MB under largeHeap)
    // for decoded bitmaps, avoiding recurring decompression bottlenecks during continuous scrolling.
    private val maxMemoryBytes = Runtime.getRuntime().maxMemory()
    private val memoryCacheSize = (maxMemoryBytes * 0.35).toInt().coerceIn(32 * 1024 * 1024, 256 * 1024 * 1024)

    private val memoryCache = object : android.util.LruCache<String, Bitmap>(memoryCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return runCatching { bitmap.allocationByteCount }.getOrDefault(1024 * 1024)
        }
    }

    data class ThumbnailResult(val bitmap: Bitmap)

    fun getFromMemory(uri: String, widthPx: Int = PREWARM_WIDTH, heightPx: Int = PREWARM_HEIGHT): Bitmap? {
        val (normalizedWidth, normalizedHeight) = normalizedDimensions(widthPx, heightPx)
        val key = cacheKey(uri, normalizedWidth, normalizedHeight)
        val cached = memoryCache.get(key)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    fun putInMemory(uri: String, widthPx: Int, heightPx: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val (normalizedWidth, normalizedHeight) = normalizedDimensions(widthPx, heightPx)
        val key = cacheKey(uri, normalizedWidth, normalizedHeight)
        memoryCache.put(key, bitmap)
    }

    fun trimMemory(level: Int) {
        runCatching {
            when {
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    memoryCache.evictAll()
                }
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                    memoryCache.trimToSize(memoryCacheSize / 2)
                }
            }
        }
    }

    fun clearMemory() {
        runCatching { memoryCache.evictAll() }
    }

    fun hasPrepared(context: Context, uri: String, widthPx: Int = PREWARM_WIDTH, heightPx: Int = PREWARM_HEIGHT): Boolean {
        val (normalizedWidth, normalizedHeight) = normalizedDimensions(widthPx, heightPx)
        val key = cacheKey(uri, normalizedWidth, normalizedHeight)
        if (memoryCache.get(key) != null) return true
        val file = cacheFile(context.applicationContext, uri, widthPx, heightPx)
        return file.isFile && file.length() > 0L
    }

    fun hasStorageHeadroom(context: Context): Boolean = runCatching {
        StatFs(context.applicationContext.cacheDir.absolutePath).availableBytes > FREE_SPACE_RESERVE_BYTES
    }.getOrDefault(false)

    /**
     * Returns a prepared thumbnail with multi-tier caching:
     * Tier 1: In-Memory LruCache (0ms, zero allocations)
     * Tier 2: Disk WebP cache decoded directly to hardware bitmap in GPU memory
     * Tier 3: Android ContentResolver loadThumbnail with non-blocking async disk persistence
     */
    suspend fun loadOrCreate(
        context: Context,
        uri: Uri,
        widthPx: Int,
        heightPx: Int
    ): ThumbnailResult? {
        val appContext = context.applicationContext
        val (targetWidth, targetHeight) = normalizedDimensions(widthPx, heightPx)
        val key = cacheKey(uri.toString(), targetWidth, targetHeight)

        // Tier 1: In-Memory cache lookup
        val inMem = memoryCache.get(key)
        if (inMem != null && !inMem.isRecycled) {
            return ThumbnailResult(inMem)
        }

        val file = File(cacheDirectory(appContext), "$key.webp")

        // Tier 2: Disk cache lookup (Direct Hardware Bitmap decode)
        readPrepared(file)?.let { diskBitmap ->
            memoryCache.put(key, diskBitmap)
            return ThumbnailResult(diskBitmap)
        }

        // Tier 3: ContentResolver thumbnail query
        val stripeIndex = (key.hashCode() and Int.MAX_VALUE) % uriLocks.size
        val mutex = uriLocks[stripeIndex]
        return mutex.withLock {
            currentCoroutineContext().ensureActive()

            // Re-check memory and disk under lock to prevent redundant queries
            val memInside = memoryCache.get(key)
            if (memInside != null && !memInside.isRecycled) {
                return@withLock ThumbnailResult(memInside)
            }
            readPrepared(file)?.let { diskBitmap ->
                memoryCache.put(key, diskBitmap)
                return@withLock ThumbnailResult(diskBitmap)
            }

            val bitmap = loadMediaStoreThumbnail(appContext, uri, targetWidth, targetHeight)
                ?: return@withLock null

            // Instantly cache in memory so UI and Coil get immediate frame delivery
            memoryCache.put(key, bitmap)

            // Asynchronously persist to disk so the fetcher never blocks on WebP compression
            diskWriteScope.launch {
                writePreparedIfSafe(appContext, file, bitmap)
            }

            ThumbnailResult(bitmap)
        }
    }

    suspend fun prepareIfMissing(context: Context, uri: String): Boolean {
        val (targetWidth, targetHeight) = normalizedDimensions(PREWARM_WIDTH, PREWARM_HEIGHT)
        val key = cacheKey(uri, targetWidth, targetHeight)
        val inMem = memoryCache.get(key)
        if (inMem != null && !inMem.isRecycled) return true

        val file = cacheFile(context.applicationContext, uri, PREWARM_WIDTH, PREWARM_HEIGHT)
        if (file.isFile && file.length() > 0L) {
            readPrepared(file)?.let {
                memoryCache.put(key, it)
                return true
            }
        }

        if (!hasStorageHeadroom(context)) return false

        val result = loadOrCreate(context, Uri.parse(uri), PREWARM_WIDTH, PREWARM_HEIGHT)
        return result != null
    }

    private fun cacheDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME)

    private fun cacheFile(context: Context, uri: String, widthPx: Int, heightPx: Int): File {
        val (normalizedWidth, normalizedHeight) = normalizedDimensions(widthPx, heightPx)
        val key = cacheKey(uri, normalizedWidth, normalizedHeight)
        return File(cacheDirectory(context), "$key.webp")
    }

    private fun normalizedDimensions(widthPx: Int, heightPx: Int): Pair<Int, Int> {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val largest = maxOf(width, height)
        return if (largest in 160..PREWARM_WIDTH) {
            PREWARM_WIDTH to PREWARM_HEIGHT
        } else {
            width.coerceIn(96, 512) to height.coerceIn(96, 512)
        }
    }

    private fun cacheKey(uri: String, widthPx: Int, heightPx: Int): String {
        val normalizedWidth = widthPx.coerceIn(96, 512)
        val normalizedHeight = heightPx.coerceIn(96, 512)
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$uri|$normalizedWidth|$normalizedHeight".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun readPrepared(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Bitmap.Config.HARDWARE
            } else {
                Bitmap.Config.RGB_565
            }
        }
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
        if (bitmap == null) {
            runCatching { file.delete() }
        }
        return bitmap
    }

    private fun writePreparedIfSafe(context: Context, file: File, bitmap: Bitmap) {
        if (!hasStorageHeadroom(context)) return
        val parent = file.parentFile ?: return
        if (!parent.exists() && !parent.mkdirs()) return

        val temporary = File(parent, file.nameWithoutExtension + ".tmp")
        runCatching {
            temporary.delete()
            FileOutputStream(temporary).use { stream ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                // If bitmap is Hardware-backed, extract software bitmap for compression
                val compressableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                } else {
                    bitmap
                }
                if (!compressableBitmap.compress(format, WEBP_QUALITY, stream)) {
                    throw IllegalStateException("Could not encode prepared thumbnail")
                }
            }

            if (!temporary.renameTo(file)) {
                file.delete()
                if (!temporary.renameTo(file)) {
                    temporary.delete()
                    return@runCatching
                }
            }
        }
        if (temporary.exists()) runCatching { temporary.delete() }
    }

    private suspend fun loadMediaStoreThumbnail(
        context: Context,
        uri: Uri,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val targetWidth = widthPx.coerceIn(96, 512)
        val targetHeight = heightPx.coerceIn(96, 512)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signal = CancellationSignal()
                val job = currentCoroutineContext()[Job]
                val cancellationHandle = job?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) signal.cancel()
                }
                try {
                    context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), signal)
                } finally {
                    cancellationHandle?.dispose()
                }
            } else {
                val id = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(-1L)
                if (id < 0L) {
                    null
                } else {
                    val isVideo = uri.path?.contains("/video/") == true
                    if (isVideo) {
                        MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
                        )
                    } else {
                        MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cancelled: OperationCanceledException) {
            throw CancellationException("Thumbnail fetch cancelled", cancelled)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Process-lifetime prewarmer with scroll-adaptive throttling.
 * When the user is actively flinging or dragging, it pauses immediately to give
 * 100% of CPU and disk I/O throughput to the visible screen elements.
 */
object ThumbnailPrewarmManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var worker: Job? = null
    private var activeUris: List<String>? = null
    private var completedUris: List<String>? = null
    private val priorityQueue = java.util.ArrayDeque<MediaItem>()
    private val queuedPriorityUris = HashSet<String>()

    @Volatile
    var isScrollPaused: Boolean = false

    fun prioritize(items: List<MediaItem>) {
        if (items.isEmpty()) return
        synchronized(lock) {
            if (worker?.isActive != true || activeUris.isNullOrEmpty()) return
            priorityQueue.clear()
            queuedPriorityUris.clear()
            items.asSequence().distinctBy { it.uri }.take(60).forEach { item ->
                if (queuedPriorityUris.add(item.uri)) priorityQueue.addLast(item)
            }
        }
    }

    fun start(context: Context, media: List<MediaItem>) {
        val appContext = context.applicationContext
        val uris = media.map { it.uri }

        synchronized(lock) {
            if (uris.isEmpty()) {
                worker?.cancel()
                worker = null
                priorityQueue.clear()
                queuedPriorityUris.clear()
                activeUris = emptyList()
                completedUris = emptyList()
                return
            }
            if (activeUris == uris && worker?.isActive == true) return
            if (completedUris == uris) return

            worker?.cancel()
            priorityQueue.clear()
            queuedPriorityUris.clear()
            activeUris = uris
            completedUris = null
            worker = scope.launch {
                var completedAll = true
                var naturalIndex = 0
                var processedCount = 0
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()

                        // If user is actively scrolling, yield and wait so visible elements get full priority
                        if (isScrollPaused) {
                            kotlinx.coroutines.delay(40)
                            continue
                        }

                        val item = synchronized(lock) {
                            when {
                                activeUris !== uris -> null
                                priorityQueue.isNotEmpty() -> {
                                    val prioritized = priorityQueue.removeFirst()
                                    queuedPriorityUris.remove(prioritized.uri)
                                    prioritized
                                }
                                naturalIndex < media.size -> media[naturalIndex++]
                                else -> null
                            }
                        } ?: break

                        if (!PreparedThumbnailCache.hasPrepared(appContext, item.uri)) {
                            if (!PreparedThumbnailCache.hasStorageHeadroom(appContext)) {
                                completedAll = false
                                break
                            }
                            val prepared = try {
                                PreparedThumbnailCache.prepareIfMissing(appContext, item.uri)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            }
                            if (!prepared) completedAll = false
                        }
                        processedCount++
                        if (processedCount % 4 == 0) yield()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    synchronized(lock) {
                        if (activeUris === uris) {
                            worker = null
                            priorityQueue.clear()
                            queuedPriorityUris.clear()
                            if (completedAll) completedUris = uris
                        }
                    }
                }
            }
        }
    }
}
