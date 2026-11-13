package com.praveen.studio.photovideo.url.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.praveen.studio.photovideo.url.data.model.MediaCategory
import com.praveen.studio.photovideo.url.data.remote.FirebaseDatabaseManager
import com.praveen.studio.photovideo.url.domain.util.LinkRules
import com.praveen.studio.photovideo.url.domain.util.ViewerContent
import com.praveen.studio.photovideo.url.ui.theme.CyanPrimary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

private val ViewerBackground = Color(0xFF1E1E1E)
private const val MAX_PDF_BYTES = 100L * 1024L * 1024L

private val viewerHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

/**
 * Full-screen viewer for a tapped https://ivu.gt.tc/<id> link. It follows the website's rules
 * (see [LinkRules]): images are shown, videos are played, PDFs are displayed, and every other
 * document / file / APK is offered as a download - exactly like the web page does.
 */
@Composable
fun MediaViewerScreen(shortId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dbManager = remember { FirebaseDatabaseManager() }

    var attempt by remember(shortId) { mutableIntStateOf(0) }
    var content by remember(shortId) { mutableStateOf<ViewerContent?>(null) }

    LaunchedEffect(shortId, attempt) {
        content = null
        content = LinkRules.resolve(shortId) { id -> dbManager.lookupPublicLink(id) }
    }

    BackHandler(onBack = onClose)

    // The viewer is dark like the website: use light status / navigation bar icons while it is open.
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
            }
        } else {
            onDispose { }
        }
    }

    val pageUrl = "${LinkRules.SITE_URL}/$shortId"
    var showQr by remember { mutableStateOf(false) }
    val adGate = rememberRewardedActionGate()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBackground)
            // swallow touches so nothing behind the viewer reacts
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        when (val c = content) {
            null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

            is ViewerContent.Image -> ImageViewer(c, Modifier.fillMaxSize())

            is ViewerContent.Video -> VideoViewer(c, Modifier.fillMaxSize())

            is ViewerContent.Pdf -> PdfViewer(
                shortId = shortId,
                content = c,
                onDownload = { openExternally(context, c.downloadUrl ?: c.url) },
                modifier = Modifier.fillMaxSize()
            )

            is ViewerContent.Download -> DownloadCard(
                content = c,
                onDownload = { openExternally(context, c.downloadUrl) },
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerContent.Invalid -> ViewerMessage(
                title = "Invalid link",
                message = "This link is not valid.",
                onRetry = null,
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerContent.NotFound -> ViewerMessage(
                title = "Not found",
                message = "We could not find this media.",
                onRetry = null,
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerContent.Disabled -> ViewerMessage(
                title = "Link disabled",
                message = "This link has been disabled and is no longer available.",
                onRetry = null,
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerContent.Unsupported -> ViewerMessage(
                title = "Unsupported file type",
                message = "This file type cannot be displayed.",
                onRetry = null,
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerContent.Unavailable -> ViewerMessage(
                title = "Temporarily unavailable",
                message = "The service is temporarily unavailable. Please try again in a moment.",
                onRetry = { attempt++ },
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Top bar: close / copy link / share link
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewerIconButton(Icons.Default.Close, "Close", onClose)
            Spacer(modifier = Modifier.weight(1f))
            ViewerIconButton(Icons.Default.ContentCopy, "Copy link") {
                // rewarded ad first; the original copy code below runs once the ad was completed
                adGate.request(GatedAction.COPY_LINK) {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Link", pageUrl))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not copy the link", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ViewerIconButton(Icons.Default.QrCode2, "QR code") { showQr = true }
            if (showQr) {
                QrCodeDialog(link = pageUrl, onDismiss = { showQr = false })
            }
            Spacer(modifier = Modifier.width(8.dp))
            ViewerIconButton(Icons.Default.Share, "Share link") {
                // rewarded ad first; the original share code below runs once the ad was completed
                adGate.request(GatedAction.SHARE_LINK) {
                    try {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pageUrl)
                        }
                        context.startActivity(Intent.createChooser(send, "Share link"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not share the link", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            RewardedActionDialogs(adGate)
        }
    }
}

@Composable
private fun ViewerIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun ViewerMessage(
    title: String,
    message: String,
    onRetry: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = Color(0xFFF43F5E),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRetry != null) {
                Button(onClick = onRetry) { Text("Try again") }
            }
            OutlinedButton(
                onClick = onClose,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Close") }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Images
// ----------------------------------------------------------------------------------------------

@Composable
private fun ImageViewer(content: ViewerContent.Image, modifier: Modifier = Modifier) {
    // Coil (without extra modules) cannot draw SVG or animated GIF; a tiny <img> page can, just like the website.
    if (content.mime == "image/svg+xml" || content.mime == "image/gif") {
        WebImageViewer(content.url, modifier)
    } else {
        ZoomableImage(content, modifier)
    }
}

@Composable
private fun ZoomableImage(content: ViewerContent.Image, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scale by remember(content.url) { mutableFloatStateOf(1f) }
    var offset by remember(content.url) { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember(content.url) { mutableStateOf(true) }
    var failed by remember(content.url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(content.url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val maxX = boxSize.width * (newScale - 1f) / 2f
                    val maxY = boxSize.height * (newScale - 1f) / 2f
                    scale = newScale
                    offset = if (newScale <= 1f) {
                        Offset.Zero
                    } else {
                        Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }
            }
            .pointerInput(content.url) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(content.url)
                .size(2400, 2400)
                .crossfade(true)
                .build(),
            contentDescription = content.name,
            contentScale = ContentScale.Fit,
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                failed = state is AsyncImagePainter.State.Error
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
        if (failed) {
            Text(
                text = "This image could not be loaded.",
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WebImageViewer(url: String, modifier: Modifier = Modifier) {
    val html = remember(url) {
        // The url passed LinkRules.isAllowedStorageUrl (no quotes, spaces or angle brackets); only '&' needs escaping.
        val safeUrl = url.replace("&", "&amp;")
        "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#1e1e1e}" +
            "body{display:flex;align-items:center;justify-content:center}" +
            "img{max-width:100vw;max-height:100vh;object-fit:contain}</style></head>" +
            "<body><img src=\"$safeUrl\" alt=\"\"></body></html>"
    }
    val holder = remember(url) { arrayOfNulls<WebView>(1) }

    DisposableEffect(url) {
        onDispose {
            try {
                val web = holder[0]
                if (web != null) {
                    (web.parent as? ViewGroup)?.removeView(web)
                    web.destroy()
                }
            } catch (_: Throwable) {
            }
            holder[0] = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                holder[0] = this
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // big SVG / GIF files must not pile up in the WebView disk cache
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                setBackgroundColor(0xFF1E1E1E.toInt())
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}

// ----------------------------------------------------------------------------------------------
// Video
// ----------------------------------------------------------------------------------------------

@Composable
private fun VideoViewer(content: ViewerContent.Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Plain holder (not state): the view is created inside AndroidView's factory.
    val holder = remember(content.url) { arrayOfNulls<VideoView>(1) }
    var prepared by remember(content.url) { mutableStateOf(false) }
    var failed by remember(content.url) { mutableStateOf(false) }
    var playing by remember(content.url) { mutableStateOf(false) }
    var position by remember(content.url) { mutableIntStateOf(0) }
    var duration by remember(content.url) { mutableIntStateOf(0) }
    var dragging by remember(content.url) { mutableStateOf(false) }

    // Pause when the app goes to the background
    DisposableEffect(lifecycleOwner, content.url) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val vv = holder[0]
                if (vv != null && prepared && vv.isPlaying) {
                    vv.pause()
                    playing = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Release the player when the viewer closes
    DisposableEffect(content.url) {
        onDispose {
            try {
                holder[0]?.stopPlayback()
            } catch (_: Throwable) {
            }
            holder[0] = null
        }
    }

    // Keep the seek bar in sync with the player
    LaunchedEffect(content.url, prepared) {
        while (prepared) {
            val vv = holder[0]
            if (vv != null && !dragging) {
                try {
                    position = vv.currentPosition
                    playing = vv.isPlaying
                } catch (_: Throwable) {
                }
            }
            delay(250)
        }
    }

    val togglePlayback: () -> Unit = {
        val vv = holder[0]
        if (vv != null && prepared) {
            if (vv.isPlaying) {
                vv.pause()
                playing = false
            } else {
                vv.start()
                playing = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!failed) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(content.url) { detectTapGestures { togglePlayback() } },
                factory = { ctx ->
                    val vv = VideoView(ctx)
                    holder[0] = vv
                    vv.setOnPreparedListener { mp ->
                        duration = mp.duration
                        prepared = true
                        vv.seekTo(1) // show the first frame, like <video preload="metadata">
                    }
                    vv.setOnCompletionListener {
                        playing = false
                        position = duration
                    }
                    vv.setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                    vv.setVideoURI(Uri.parse(content.url))
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(
                            vv,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER
                            )
                        )
                    }
                }
            )
        }

        if (!prepared && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        if (failed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "This video could not be loaded.",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    try {
                        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(content.url), content.mime)
                        context.startActivity(view)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app can play this video", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open with another app") }
            }
        }

        if (prepared && !playing && !dragging) {
            IconButton(
                onClick = togglePlayback,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        if (prepared) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = togglePlayback) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = Color.White
                    )
                }
                Text(text = formatTime(position), color = Color.White, fontSize = 12.sp)
                Slider(
                    value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { fraction ->
                        dragging = true
                        position = (fraction * duration).toInt()
                    },
                    onValueChangeFinished = {
                        holder[0]?.seekTo(position)
                        dragging = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(text = formatTime(duration), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// ----------------------------------------------------------------------------------------------
// PDF
// ----------------------------------------------------------------------------------------------

private sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val doc: PdfDoc) : PdfState
    data object Failed : PdfState
}

/** Downloaded PDF + renderer. PdfRenderer can only have one page open at a time, so access is serialised. */
private class PdfDoc(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val lock = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    val width = widthPx.coerceIn(200, 1800)
                    val height = ((page.height.toFloat() / page.width.toFloat()) * width).toInt().coerceIn(1, 4000)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
    }

    suspend fun close() {
        lock.withLock {
            if (closed) return@withLock
            closed = true
            try {
                renderer.close()
            } catch (_: Throwable) {
            }
            try {
                descriptor.close()
            } catch (_: Throwable) {
            }
            try {
                file.delete()
            } catch (_: Throwable) {
            }
        }
    }
}

/** Downloads the PDF into the app cache and opens it. Returns null when it cannot be shown (too big, protected, broken). */
private suspend fun downloadAndOpenPdf(context: Context, shortId: String, url: String): PdfDoc? {
    var file: File? = null
    var descriptor: ParcelFileDescriptor? = null
    return try {
        val dir = File(context.cacheDir, "viewer")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "$shortId.pdf")
        file = target

        val request = Request.Builder().url(url).build()
        viewerHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            if (body.contentLength() > MAX_PDF_BYTES) throw IOException("PDF too large")
            var total = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PDF_BYTES) throw IOException("PDF too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }

        val opened = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor = opened
        PdfDoc(target, opened, PdfRenderer(opened))
    } catch (_: Throwable) {
        try {
            descriptor?.close()
        } catch (_: Throwable) {
        }
        try {
            file?.delete()
        } catch (_: Throwable) {
        }
        null
    }
}

@Composable
private fun PdfViewer(
    shortId: String,
    content: ViewerContent.Pdf,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember(content.url) { mutableStateOf<PdfState>(PdfState.Loading) }

    LaunchedEffect(content.url) {
        state = PdfState.Loading
        val doc = withContext(Dispatchers.IO) { downloadAndOpenPdf(context, shortId, content.url) }
        state = if (doc != null) PdfState.Ready(doc) else PdfState.Failed
    }

    DisposableEffect(content.url) {
        onDispose {
            val ready = state as? PdfState.Ready
            if (ready != null) {
                CoroutineScope(Dispatchers.IO).launch { ready.doc.close() }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            PdfState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )

            PdfState.Failed -> Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "This PDF cannot be displayed here.",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download PDF")
                }
            }

            is PdfState.Ready -> {
                val doc = current.doc
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 64.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(count = doc.pageCount) { index ->
                            PdfPage(doc = doc, index = index, widthPx = widthPx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = doc.renderPage(index, widthPx)
    }
    val rendered = bitmap
    if (rendered != null) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Documents / files / APKs: the website redirects these to a download
// ----------------------------------------------------------------------------------------------

@Composable
private fun DownloadCard(
    content: ViewerContent.Download,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = when (content.type) {
        "apk" -> MediaCategory.APKS
        "document" -> MediaCategory.DOCUMENTS
        else -> MediaCategory.FILES
    }
    val note = when (content.type) {
        "apk" -> "Android package. It is only downloaded - nothing is installed automatically."
        "document" -> "Document. It will be downloaded to your device."
        else -> "File. It will be downloaded to your device."
    }

    Column(
        modifier = modifier
            .padding(32.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = category.getIcon(),
            contentDescription = null,
            tint = CyanPrimary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = content.name,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = note,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDownload,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download")
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------------------------

/** Opens an https URL in the browser / download manager (the supabase.co URL is not handled by this app). */
private fun openExternally(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "No app available to open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
