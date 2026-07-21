package com.parsfilo.contentapp.feature.audio.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.common.util.concurrent.ListenableFuture
import com.parsfilo.contentapp.core.firebase.config.EndpointsProvider
import com.parsfilo.contentapp.feature.audio.service.AudioService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.coroutines.resume

private const val ASSET_PACK_NAME = "audioassets"
private const val STATE_KEY_POSITION = "playback_position"
private const val AUDIO_CACHE_DIR = "audio_cache"
private const val AUDIO_BUFFER_SIZE = 8 * 1024

private data class RemoteAudioManifest(
    val packageAudio: Map<String, String>,
    val availableKeys: Set<String>,
)

private data class RemoteAudioSource(
    val key: String,
    val url: String,
)

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetPackManager: AssetPackManager,
    private val endpointsProvider: EndpointsProvider,
    private val okHttpClient: OkHttpClient,
    @javax.inject.Named("audioFileName") private val audioFileName: String,
    @javax.inject.Named("useAssetPackAudio") private val useAssetPackAudio: Boolean,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    private var playerListener: Player.Listener? = null
    private var positionTrackingJob: Job? = null
    private var assetPackWaitJob: Job? = null

    // Hold reference to avoid garbage collection and allow cleanup
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null
    private var overrideAudioFileName: String? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, AudioService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        this.controllerFuture = controllerFuture

        controllerFuture.addListener({
            try {
                player = controllerFuture.get()
                checkAndLoadAudio()
            } catch (e: CancellationException) {
                Timber.w(e, "AudioService controller init cancelled")
            } catch (e: ExecutionException) {
                Timber.e(e, "Failed to connect to AudioService")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Timber.e(e, "AudioService controller init interrupted")
            } catch (e: IllegalStateException) {
                Timber.e(e, "Failed to connect to AudioService")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Audio source precedence:
     * 1. Persisted app cache (downloaded once from remote)
     * 2. App assets (if bundled for debug or legacy)
     * 3. Cloudflare remote (download-and-store first, stream fallback)
     * 4. Play Asset Pack (legacy fallback)
     *
     * This allows offline playback after first successful online load.
     */
    private fun checkAndLoadAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            val effectiveAudioFileName = resolveEffectiveAudioFileName()
            Timber.i(
                "Ses yükleme başlatıldı. useAssetPackAudio=$useAssetPackAudio, Pack=$ASSET_PACK_NAME, Dosya=$effectiveAudioFileName"
            )

            // Try 1: Persistent cached audio file from previous online download.
            val cachedAudioPath = findCachedAudioPath(
                candidates = listOf(effectiveAudioFileName, "audio.mp3")
            )
            if (cachedAudioPath != null) {
                Timber.i("✅ Kalıcı cache dosyasından bulundu: $cachedAudioPath")
                loadAudioFromFile(cachedAudioPath)
                return@launch
            }

            // Try 2: Flavor asset bundled directly into app.
            if (hasAssetInAssets(effectiveAudioFileName)) {
                Timber.i("✅ Assets klasöründen bulundu: $effectiveAudioFileName")
                loadAudioFromAssets(effectiveAudioFileName)
                return@launch
            }
            Timber.d("$effectiveAudioFileName assets'te yok, legacy fallback deneniyor...")

            // Try 3: Legacy fallback — old file name (audio.mp3) from before migration.
            if (hasAssetInAssets("audio.mp3")) {
                Timber.i("✅ Legacy assets dosyasından bulundu: audio.mp3")
                loadAudioFromAssets("audio.mp3")
                return@launch
            }

            // Try 4: Cloudflare R2 (download once and persist, then play local).
            val remoteAudio = resolveRemoteAudioSource(effectiveAudioFileName)
            if (remoteAudio != null) {
                Timber.i("✅ Cloudflare kaynağı bulundu: ${remoteAudio.key}")
                val downloadedPath = downloadAndPersistRemoteAudio(remoteAudio)
                if (downloadedPath != null) {
                    loadAudioFromFile(downloadedPath)
                } else {
                    Timber.w("⚠️ Uzak ses indirilemedi, stream fallback deneniyor.")
                    loadAudioFromRemote(remoteAudio.url)
                }
                return@launch
            }

            if (!useAssetPackAudio) {
                Timber.w("Asset pack devre dışı ve assets içinde ses bulunamadı.")
                _playerState.value = _playerState.value.copy(
                    assetLoading = false,
                    assetReady = false,
                    assetError = "Ses dosyası bulunamadı"
                )
                return@launch
            }

            // Try 5: Check asset pack location (legacy path, if enabled).
            val assetPackPath = getAssetPackFilePath(effectiveAudioFileName)
            if (assetPackPath != null) {
                Timber.i("✅ Asset pack yolundan bulundu: $assetPackPath")
                loadAudioFromFile(assetPackPath)
                return@launch
            }
            Timber.d("Asset pack yolunda dosya yok, assets fallback deneniyor...")
            Timber.w("Hiçbir yerel dosya bulunamadı, AssetPackManager sorgulanıyor...")

            // Try 6: Check if asset pack needs to be fetched (fast-follow/on-demand scenario).
            Timber.d("AssetPackManager.getPackStates() çağrılıyor...")
            val packStates = assetPackManager.getPackStates(listOf(ASSET_PACK_NAME))
            packStates.addOnSuccessListener { state ->
                val packState = state.packStates()[ASSET_PACK_NAME]
                val status = packState?.status() ?: AssetPackStatus.UNKNOWN
                val bytesDownloaded = packState?.bytesDownloaded() ?: 0
                val totalBytes = packState?.totalBytesToDownload() ?: 0
                val errorCode = packState?.errorCode() ?: 0
                Timber.i(
                    "Pack durumu: status=${statusToString(status)}, " +
                        "indirilen=$bytesDownloaded/$totalBytes bytes, errorCode=$errorCode"
                )

                when (status) {
                    AssetPackStatus.COMPLETED -> {
                        Timber.i("✅ Pack durumu: COMPLETED")
                        viewModelScope.launch(Dispatchers.IO) {
                            val path = getAssetPackFilePath(effectiveAudioFileName)
                            if (path != null) {
                                Timber.i("✅ Pack dosya yolu bulundu: $path")
                                loadAudioFromFile(path)
                            } else {
                                Timber.e("❌ Pack COMPLETED ama dosya yolu bulunamadı!")
                                _playerState.value = _playerState.value.copy(
                                    assetLoading = false,
                                    assetReady = false,
                                    assetError = "Ses dosyası bulunamadı"
                                )
                            }
                        }
                    }
                    AssetPackStatus.NOT_INSTALLED -> {
                        Timber.w("⚠️ Pack durumu: NOT_INSTALLED — fetch başlatılıyor")
                        requestAssetPackFetch(effectiveAudioFileName)
                    }
                    AssetPackStatus.DOWNLOADING -> {
                        val pct = if (totalBytes > 0) (bytesDownloaded * 100 / totalBytes) else 0
                        Timber.i("⏳ Pack durumu: DOWNLOADING — %$pct ($bytesDownloaded/$totalBytes)")
                        startAssetPackWait(effectiveAudioFileName)
                    }
                    AssetPackStatus.TRANSFERRING -> {
                        Timber.i("⏳ Pack durumu: TRANSFERRING — dosyalar aktarılıyor")
                        startAssetPackWait(effectiveAudioFileName)
                    }
                    AssetPackStatus.WAITING_FOR_WIFI -> {
                        Timber.w("⚠️ Pack durumu: WAITING_FOR_WIFI — WiFi bekleniyor")
                        _playerState.value = _playerState.value.copy(
                            assetLoading = true,
                            assetError = "WiFi bağlantısı bekleniyor..."
                        )
                        startAssetPackWait(effectiveAudioFileName)
                    }
                    AssetPackStatus.PENDING -> {
                        Timber.i("⏳ Pack durumu: PENDING — indirme kuyruğunda")
                        startAssetPackWait(effectiveAudioFileName)
                    }
                    AssetPackStatus.CANCELED -> {
                        Timber.w("⚠️ Pack durumu: CANCELED")
                        _playerState.value = _playerState.value.copy(
                            assetLoading = false,
                            assetReady = false,
                            assetError = "İndirme iptal edildi"
                        )
                    }
                    AssetPackStatus.FAILED -> {
                        Timber.e("❌ Pack durumu: FAILED — errorCode=$errorCode")
                        _playerState.value = _playerState.value.copy(
                            assetLoading = false,
                            assetReady = false,
                            assetError = "İndirme başarısız (hata: $errorCode)"
                        )
                    }
                    else -> {
                        Timber.w("⚠️ Pack durumu: UNKNOWN ($status)")
                        _playerState.value = _playerState.value.copy(
                            assetLoading = false,
                            assetReady = false,
                            assetError = "Ses dosyası hazırlanıyor..."
                        )
                    }
                }
            }.addOnFailureListener { e ->
                Timber.e("❌ AssetPackManager.getPackStates() başarısız", e)
                _playerState.value = _playerState.value.copy(
                    assetLoading = false,
                    assetReady = false,
                    assetError = "Ses dosyası henüz mevcut değil"
                )
            }
        }
    }

    internal fun getAssetPackFilePath(targetFileName: String): String? {
        val packLocation = assetPackManager.getPackLocation(ASSET_PACK_NAME)
        if (packLocation == null) {
            Timber.d("getPackLocation($ASSET_PACK_NAME) = null")
            return null
        }
        val assetsPath = packLocation.assetsPath()
        if (assetsPath == null) {
            Timber.d("assetsPath() = null (packPath=${packLocation.path()})")
            return null
        }
        val file = File(assetsPath, targetFileName)
        Timber.d("Asset pack file checked exists=%s", file.exists())
        return if (file.exists()) file.absolutePath else null
    }

    private fun hasAssetInAssets(fileName: String): Boolean {
        return try {
            context.assets.open(fileName).use { true }
        } catch (e: IOException) {
            Timber.d(e, "Asset bulunamadı: %s", fileName)
            false
        }
    }

    private fun loadAudioFromFile(filePath: String) {
        Timber.d("Loading audio from local file")
        _playerState.value = _playerState.value.copy(
            assetReady = true,
            assetLoading = false,
            assetError = null
        )
        viewModelScope.launch(Dispatchers.Main.immediate) {
            setupPlayer(MediaItem.fromUri(Uri.fromFile(File(filePath))))
        }
    }

    private fun loadAudioFromAssets(fileName: String) {
        Timber.i("▶ Ses yükleniyor (assets): $fileName")
        _playerState.value = _playerState.value.copy(
            assetReady = true,
            assetLoading = false,
            assetError = null
        )
        viewModelScope.launch(Dispatchers.Main.immediate) {
            setupPlayer(MediaItem.fromUri("asset:///$fileName"))
        }
    }

    private fun loadAudioFromRemote(remoteUrl: String) {
        Timber.d("Loading audio from remote source")
        _playerState.value = _playerState.value.copy(
            assetReady = true,
            assetLoading = false,
            assetError = null
        )
        viewModelScope.launch(Dispatchers.Main.immediate) {
            setupPlayer(MediaItem.fromUri(remoteUrl))
        }
    }

    private fun resolveRemoteAudioSource(effectiveAudioFileName: String): RemoteAudioSource? {
        val manifest = fetchRemoteAudioManifest() ?: return null
        val packageName = context.packageName
        val audioBaseUrl = endpointsProvider.getAudioBaseUrl()
        val keyFromPackage = manifest.packageAudio[packageName]
        val preferredKey = if (!keyFromPackage.isNullOrBlank()) keyFromPackage else effectiveAudioFileName
        val normalizedKey = normalizeAudioFileName(preferredKey) ?: effectiveAudioFileName

        if (manifest.availableKeys.contains(normalizedKey)) {
            return RemoteAudioSource(
                key = normalizedKey,
                url = "$audioBaseUrl/${Uri.encode(normalizedKey)}"
            )
        }

        if (manifest.availableKeys.contains(effectiveAudioFileName)) {
            return RemoteAudioSource(
                key = effectiveAudioFileName,
                url = "$audioBaseUrl/${Uri.encode(effectiveAudioFileName)}"
            )
        }

        if (manifest.availableKeys.contains("audio.mp3")) {
            return RemoteAudioSource(
                key = "audio.mp3",
                url = "$audioBaseUrl/audio.mp3"
            )
        }

        Timber.w(
            "Cloudflare manifest içinde uygun ses bulunamadı. " +
                "package=$packageName preferred=$normalizedKey file=$effectiveAudioFileName"
        )
        return null
    }

    private fun resolveEffectiveAudioFileName(): String {
        return normalizeAudioFileName(overrideAudioFileName)
            ?: normalizeAudioFileName(audioFileName)
            ?: "audio.mp3"
    }

    private fun normalizeAudioFileName(rawValue: String?): String? {
        val normalized = rawValue?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return null
        return if (normalized.endsWith(".mp3")) normalized else "$normalized.mp3"
    }

    private fun findCachedAudioPath(candidates: List<String>): String? {
        candidates.forEach { candidate ->
            val file = cachedAudioFile(candidate)
            if (file.exists() && file.length() > 0L) {
                return file.absolutePath
            }
        }
        return null
    }

    private fun downloadAndPersistRemoteAudio(source: RemoteAudioSource): String? {
        val target = cachedAudioFile(source.key)
        if (target.exists() && target.length() > 0L) {
            Timber.d("Remote audio already exists in persistent cache")
            return target.absolutePath
        }

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")

        try {
            _playerState.value = _playerState.value.copy(
                assetLoading = true,
                assetError = "Ses dosyası indiriliyor..."
            )
            val request = Request.Builder()
                .url(source.url)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("Uzak ses indirme başarısız. key=%s code=%s", source.key, response.code)
                return null
            }
            val responseBody = response.body

            BufferedInputStream(responseBody.byteStream()).use { input ->
                BufferedOutputStream(temp.outputStream()).use { output ->
                    val buffer = ByteArray(AUDIO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            if (!temp.exists() || temp.length() == 0L) {
                Timber.w("Uzak ses indirildi ancak boş dosya oluştu. key=${source.key}")
                temp.delete()
                return null
            }

            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            Timber.d("Remote audio persisted to cache")
            return target.absolutePath
        } catch (e: IOException) {
            Timber.w(e, "Uzak ses indirme hatası. key=${source.key}")
            temp.delete()
            return null
        } catch (e: SecurityException) {
            Timber.w(e, "Uzak ses indirme hatası. key=${source.key}")
            temp.delete()
            return null
        }
    }

    private fun cachedAudioFile(fileName: String): File {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(File(context.filesDir, AUDIO_CACHE_DIR), safeName)
    }

    private fun fetchRemoteAudioManifest(): RemoteAudioManifest? {
        return try {
            val request = Request.Builder()
                .url(endpointsProvider.getAudioManifestUrl())
                .addHeader("Accept", "application/json")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.w("Cloudflare manifest alınamadı. code=%s", response.code)
                return null
            }

            val body = response.body.string()
            val json = JSONObject(body)
            val packageAudio = mutableMapOf<String, String>()
            val packageAudioObj = json.optJSONObject("packageAudio")
            if (packageAudioObj != null) {
                val keys = packageAudioObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = packageAudioObj.optString(key).trim()
                    if (value.isNotEmpty()) {
                        packageAudio[key] = value
                    }
                }
            }

            val availableKeys = mutableSetOf<String>()
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val item = files.optJSONObject(i) ?: continue
                    val key = item.optString("key").trim()
                    if (key.isNotEmpty()) {
                        availableKeys.add(key)
                    }
                }
            }

            if (availableKeys.isEmpty()) {
                Timber.w("Cloudflare manifest boş döndü.")
                return null
            }

            RemoteAudioManifest(
                packageAudio = packageAudio,
                availableKeys = availableKeys
            )
        } catch (e: IOException) {
            Timber.w(e, "Cloudflare manifest okunamadı")
            null
        } catch (e: JSONException) {
            Timber.w(e, "Cloudflare manifest parse edilemedi")
            null
        } catch (e: SecurityException) {
            Timber.w(e, "Cloudflare manifest okunamadı")
            null
        }
    }

    private fun requestAssetPackFetch(targetFileName: String) {
        Timber.i("⬇️ Asset pack fetch başlatılıyor: $ASSET_PACK_NAME")
        _playerState.value = _playerState.value.copy(
            assetLoading = true,
            assetError = "Ses dosyası indiriliyor..."
        )
        val request = assetPackManager.fetch(listOf(ASSET_PACK_NAME))
        request.addOnSuccessListener {
            Timber.i("✅ Fetch isteği kabul edildi, pack bekleniyor...")
            startAssetPackWait(targetFileName)
        }.addOnFailureListener { e ->
            Timber.e("❌ Fetch başarısız: ${e.message}", e)
            _playerState.value = _playerState.value.copy(
                assetLoading = false,
                assetReady = false,
                assetError = "Ses dosyası indirilemedi: ${e.message}"
            )
        }
    }

    private fun startAssetPackWait(targetFileName: String) {
        if (assetPackWaitJob?.isActive == true) {
            Timber.d("Asset pack wait already active, skipping duplicate listener registration")
            return
        }
        assetPackWaitJob = viewModelScope.launch(Dispatchers.IO) {
            _playerState.value = _playerState.value.copy(
                assetLoading = true,
                assetError = "Ses dosyası hazırlanıyor..."
            )
            val path = awaitAssetPackReady(targetFileName)
            if (path != null) {
                loadAudioFromFile(path)
                return@launch
            }
            Timber.e("❌ Asset pack bekleme listener'ı başarısız/zaman aşımı")
            _playerState.value = _playerState.value.copy(
                assetLoading = false,
                assetReady = false,
                assetError = "Ses dosyası hazırlanamadı"
            )
        }
    }

    private suspend fun awaitAssetPackReady(targetFileName: String): String? =
        suspendCancellableCoroutine { continuation ->
            val existingPath = getAssetPackFilePath(targetFileName)
            if (existingPath != null) {
                continuation.resume(existingPath)
                return@suspendCancellableCoroutine
            }

            val listener = object : AssetPackStateUpdateListener {
                override fun onStateUpdate(state: AssetPackState) {
                    if (state.name() != ASSET_PACK_NAME) return
                    when (state.status()) {
                        AssetPackStatus.COMPLETED -> {
                            val resolved = getAssetPackFilePath(targetFileName)
                            assetPackManager.unregisterListener(this)
                            continuation.resume(resolved)
                        }

                        AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                            Timber.w(
                                "Asset pack listener failed. status=%s error=%s",
                                statusToString(state.status()),
                                state.errorCode()
                            )
                            assetPackManager.unregisterListener(this)
                            continuation.resume(null)
                        }

                        else -> {
                            // waiting states handled by listener until terminal status.
                        }
                    }
                }
            }

            assetPackManager.registerListener(listener)
            continuation.invokeOnCancellation {
                assetPackManager.unregisterListener(listener)
            }
        }

    private fun setupPlayer(mediaItem: MediaItem) {
        val player = this.player ?: return
        // Remove any previously added listener to prevent accumulation
        playerListener?.let { player.removeListener(it) }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
                if (playbackState == Player.STATE_READY && player.isPlaying) {
                    startPositionTracking()
                }
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    stopPositionTracking()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    startPositionTracking()
                } else {
                    stopPositionTracking()
                }
            }
        }
        playerListener = listener
        player.addListener(listener)

        if (player.mediaItemCount == 0) {
            player.setMediaItem(mediaItem)

            // Restore position if available
            val savedPosition = savedStateHandle.get<Long>(STATE_KEY_POSITION)
            if (savedPosition != null && savedPosition > 0) {
                Timber.i("Restoring saved position: $savedPosition")
                player.seekTo(savedPosition)
            }

            player.prepare()
        } else {
            updateState()
            if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                startPositionTracking()
            }
        }
    }

    internal fun startPositionTracking() {
        if (positionTrackingJob?.isActive == true) return
        positionTrackingJob = viewModelScope.launch {
            while (coroutineContext[Job]?.isActive == true) {
                updateState()
                delay(500)
            }
        }
    }

    internal fun stopPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = null
    }

    internal fun updateState() {
        val player = this.player ?: return
        val currentPos = player.currentPosition.coerceAtLeast(0)

        _playerState.value = _playerState.value.copy(
            isPlaying = player.isPlaying,
            currentPosition = currentPos,
            duration = player.duration.coerceAtLeast(0),
            isLoaded = player.playbackState == Player.STATE_READY ||
                player.playbackState == Player.STATE_BUFFERING
        )

        // Save state for process death
        savedStateHandle[STATE_KEY_POSITION] = currentPos
    }

    fun play() {
        Timber.d("AudioPlayer action=play")
        player?.play()
    }

    fun playFromUrl(url: String) {
        Timber.d("AudioPlayer action=playFromUrl url=%s", url)
        val mediaPlayer = player ?: return
        val mediaItem = MediaItem.fromUri(url.toUri())

        savedStateHandle[STATE_KEY_POSITION] = 0L
        stopPositionTracking()

        mediaPlayer.pause()
        mediaPlayer.clearMediaItems()
        mediaPlayer.setMediaItem(mediaItem)
        mediaPlayer.prepare()
        mediaPlayer.play()

        _playerState.value = _playerState.value.copy(
            assetReady = true,
            assetLoading = false,
            assetError = null,
        )
        updateState()
    }

    fun pause() {
        Timber.d("AudioPlayer action=pause")
        player?.pause()
    }

    fun stop() {
        Timber.d("AudioPlayer action=stop")
        player?.pause()
        player?.seekTo(0)
        stopPositionTracking()
        updateState()
    }

    /**
     * App arka plana alındığında sesi güvenli şekilde durdurur.
     * Kullanıcı geri döndüğünde oynatma otomatik başlamaz.
     */
    fun stopForAppBackground() {
        Timber.d("AudioPlayer action=stopForAppBackground")
        val player = this.player ?: return
        if (player.isPlaying || player.currentPosition > 0L) {
            player.pause()
            player.seekTo(0)
        }
        stopPositionTracking()
        updateState()
    }

    fun togglePlayPause() {
        val player = this.player ?: return
        Timber.d("AudioPlayer action=togglePlayPause currentlyPlaying=%s", player.isPlaying)
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(position: Long) {
        Timber.d("AudioPlayer action=seekTo position=%d", position)
        player?.seekTo(position)
        updateState()
    }

    fun skipForward() {
        val player = this.player ?: return
        val newPos = (player.currentPosition + 10_000).coerceAtMost(player.duration)
        Timber.d("AudioPlayer action=skipForward newPosition=%d", newPos)
        player.seekTo(newPos)
    }

    fun skipBackward() {
        val player = this.player ?: return
        val newPos = (player.currentPosition - 10_000).coerceAtLeast(0)
        Timber.d("AudioPlayer action=skipBackward newPosition=%d", newPos)
        player.seekTo(newPos)
    }

    /** Retry loading the asset pack if it failed previously */
    fun retryAssetLoad() {
        Timber.i("🔄 Tekrar deneniyor...")
        _playerState.value = _playerState.value.copy(
            assetLoading = true,
            assetError = null
        )
        // Only retry if player is connected
        if (player != null) {
            checkAndLoadAudio()
        }
    }

    /**
     * Prayer detail ekranında sureye göre ses değiştirir.
     * null verilirse flavor default ses dosyasına döner.
     */
    fun setOverrideAudioFileName(fileName: String?) {
        val normalized = normalizeAudioFileName(fileName)
        if (overrideAudioFileName == normalized) return

        Timber.d("AudioPlayer override file changed old=%s new=%s", overrideAudioFileName, normalized)
        overrideAudioFileName = normalized
        savedStateHandle[STATE_KEY_POSITION] = 0L
        stopPositionTracking()

        player?.let { mediaPlayer ->
            mediaPlayer.pause()
            mediaPlayer.clearMediaItems()
        }

        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentPosition = 0L,
            duration = 0L,
            isLoaded = false,
            assetLoading = true,
            assetError = null
        )

        if (player != null) {
            checkAndLoadAudio()
        }
    }

    internal fun statusToString(status: Int): String = when (status) {
        AssetPackStatus.UNKNOWN -> "UNKNOWN"
        AssetPackStatus.PENDING -> "PENDING"
        AssetPackStatus.DOWNLOADING -> "DOWNLOADING"
        AssetPackStatus.TRANSFERRING -> "TRANSFERRING"
        AssetPackStatus.COMPLETED -> "COMPLETED"
        AssetPackStatus.FAILED -> "FAILED"
        AssetPackStatus.CANCELED -> "CANCELED"
        AssetPackStatus.WAITING_FOR_WIFI -> "WAITING_FOR_WIFI"
        AssetPackStatus.NOT_INSTALLED -> "NOT_INSTALLED"
        AssetPackStatus.REQUIRES_USER_CONFIRMATION -> "REQUIRES_USER_CONFIRMATION"
        else -> "UNKNOWN($status)"
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel temizleniyor — listener temizleniyor")
        stopPositionTracking()
        assetPackWaitJob?.cancel()
        assetPackWaitJob = null
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        // MediaController release
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}
