package ru.relsokhod.spb.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import ru.relsokhod.spb.BuildConfig
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.CityId
import ru.relsokhod.spb.model.GeneratedTrip
import ru.relsokhod.spb.model.ThemeMode
import ru.relsokhod.spb.model.TramRoute
import ru.relsokhod.spb.model.TramStop

class WebTramMapBridge(
    private val mainHandler: Handler,
    private val onReady: () -> Unit,
    private val onStopPickedId: (String) -> Unit,
    private val onLandmarkPickedId: (String) -> Unit
) {
    @JavascriptInterface
    fun onMapReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onStopPicked(stopId: String?) {
        val clean = stopId?.trim().orEmpty()
        if (clean.isNotEmpty()) {
            mainHandler.post { onStopPickedId(clean) }
        }
    }

    @JavascriptInterface
    fun onLandmarkSelected(landmarkId: String?) {
        val clean = landmarkId?.trim().orEmpty()
        mainHandler.post { onLandmarkPickedId(clean) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebTramMap(
    city: CityId,
    themeMode: ThemeMode,
    accentSeed: AccentSeed,
    stops: List<TramStop>,
    routes: List<TramRoute>,
    trip: GeneratedTrip?,
    showAllNetwork: Boolean,
    includePlanned2027: Boolean,
    startStopId: String?,
    destinationStopId: String?,
    mapPickMode: String?,
    onPickStop: (TramStop) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val stopById = remember(stops) { stops.associateBy { it.id } }

    var forceOfflineCanvas by remember { mutableStateOf(false) }
    var bridgeReady by remember { mutableStateOf(false) }
    var hasLoadError by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val latestStateJson = remember(
        city,
        themeMode,
        accentSeed,
        showAllNetwork,
        includePlanned2027,
        startStopId,
        destinationStopId,
        mapPickMode,
        trip
    ) {
        WebMapBridgeHelper.serializeMapStateJson(
            city = city,
            themeMode = themeMode,
            accentSeed = accentSeed,
            showAllNetwork = showAllNetwork,
            includePlanned2027 = includePlanned2027,
            startStopId = startStopId,
            destinationStopId = destinationStopId,
            mapPickMode = mapPickMode,
            trip = trip
        )
    }

    val currentStateJson by rememberUpdatedState(latestStateJson)
    val currentStopById by rememberUpdatedState(stopById)
    val currentOnPickStop by rememberUpdatedState(onPickStop)

    val initialUrl = remember(reloadNonce) {
        WebMapBridgeHelper.buildMapOnlyUrl(
            baseUrl = BuildConfig.WEB_MAP_BASE_URL,
            city = city,
            themeMode = themeMode,
            accentSeed = accentSeed,
            showAllNetwork = showAllNetwork,
            includePlanned2027 = includePlanned2027,
            startStopId = startStopId,
            destinationStopId = destinationStopId,
            mapPickMode = mapPickMode
        )
    }

    val webMapHost = remember {
        runCatching { Uri.parse(BuildConfig.WEB_MAP_BASE_URL).host }.getOrNull().orEmpty()
    }

    val bridge = remember(mainHandler) {
        WebTramMapBridge(
            mainHandler = mainHandler,
            onReady = {
                bridgeReady = true
                hasLoadError = false
                webViewRef?.evaluateJavascript(
                    WebMapBridgeHelper.buildEvaluateJsCall(currentStateJson),
                    null
                )
            },
            onStopPickedId = { stopId ->
                currentStopById[stopId]?.let { stop ->
                    currentOnPickStop(stop)
                }
            },
            onLandmarkPickedId = { _ ->
                // Landmark preview card is displayed directly inside the Web map view
            }
        )
    }

    LaunchedEffect(latestStateJson, bridgeReady, forceOfflineCanvas) {
        if (bridgeReady && !forceOfflineCanvas) {
            webViewRef?.evaluateJavascript(
                WebMapBridgeHelper.buildEvaluateJsCall(latestStateJson),
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.let { wv ->
                wv.removeJavascriptInterface("RelsokhodAndroid")
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef = null
        }
    }

    val showWebView = !forceOfflineCanvas && !hasLoadError

    Box(modifier = modifier.fillMaxSize()) {
        // Always keep native Canvas underneath as instant placeholder and offline fallback
        if (!bridgeReady || !showWebView) {
            TramMapCanvas(
                city = city,
                stops = stops,
                routes = routes,
                trip = trip,
                showAllNetwork = showAllNetwork,
                mapPickMode = mapPickMode,
                onPickStop = onPickStop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showWebView) {
            key(reloadNonce) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (bridgeReady) 1f else 0f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.loadsImagesAutomatically = true
                            settings.allowFileAccess = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false

                            setOnTouchListener { v, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN,
                                    MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                false
                            }

                            addJavascriptInterface(bridge, "RelsokhodAndroid")

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val targetUri = request?.url ?: return null
                                    if (targetUri.scheme?.equals("https", ignoreCase = true) == true &&
                                        targetUri.host.equals(WebMapBridgeHelper.LOCAL_WEB_MAP_HOST, ignoreCase = true)
                                    ) {
                                        val assetPath = WebMapBridgeHelper.resolveLocalAssetPath(targetUri.path)
                                        if (assetPath != null) {
                                            val stream = runCatching { ctx.assets.open(assetPath) }.getOrNull()
                                            if (stream != null) {
                                                val mime = WebMapBridgeHelper.guessMimeType(assetPath)
                                                val encoding = if (
                                                    mime.startsWith("text/") ||
                                                    mime == "application/javascript" ||
                                                    mime == "application/json" ||
                                                    mime == "image/svg+xml"
                                                ) {
                                                    "UTF-8"
                                                } else {
                                                    null
                                                }
                                                return WebResourceResponse(mime, encoding, stream)
                                            }
                                        }
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            404,
                                            "Not Found",
                                            emptyMap(),
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUri = request?.url ?: return false
                                    val scheme = targetUri.scheme?.lowercase().orEmpty()
                                    val host = targetUri.host.orEmpty()
                                    val isAllowedHost =
                                        host.equals(WebMapBridgeHelper.LOCAL_WEB_MAP_HOST, ignoreCase = true) ||
                                            (webMapHost.isNotEmpty() && host.equals(webMapHost, ignoreCase = true))
                                    if (request.isForMainFrame &&
                                        (scheme == "http" || scheme == "https") &&
                                        !isAllowedHost
                                    ) {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        hasLoadError = true
                                        bridgeReady = false
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == true &&
                                        (errorResponse?.statusCode ?: 200) >= 400
                                    ) {
                                        hasLoadError = true
                                        bridgeReady = false
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!hasLoadError) {
                                        view?.evaluateJavascript(
                                            WebMapBridgeHelper.buildEvaluateJsCall(currentStateJson),
                                            null
                                        )
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(initialUrl)
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                    }
                )
            }
        }

        // Compact MD3 pill indicator / fallback switcher in top-right (clear of top-left legend)
        if (!bridgeReady || hasLoadError || forceOfflineCanvas) {
            Surface(
                onClick = {
                    if (hasLoadError || forceOfflineCanvas) {
                        forceOfflineCanvas = false
                        hasLoadError = false
                        bridgeReady = false
                        reloadNonce += 1
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!hasLoadError && !forceOfflineCanvas) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Веб-карта…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = if (hasLoadError) Icons.Rounded.Refresh else Icons.Rounded.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (hasLoadError) "Офлайн · Повторить" else "Веб-карта",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
