package io.legado.app.ui.book.read.sheet

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.icon.MaterialSymbol
import io.legado.app.ui.widget.components.icon.SymbolIcon
import io.legado.app.ui.widget.components.icon.Symbols
import io.legado.app.utils.ImageSaveUtils.saveImageToGallery
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * SPEC-057/240: fullscreen image gallery — swipe pages, drag down / X to dismiss.
 * SPEC-240: share/save as top-end actions (no long-press menu); bottom-center page index.
 * SPEC-266: pinch-zoom; Photo Library between share and save for page thumbnails.
 */
@Composable
fun ReaderImageGalleryDialog(
    show: Boolean,
    images: List<String>,
    initialIndex: Int,
    sourceOrigin: String? = null,
    onDismissRequest: () -> Unit,
) {
    if (!show || images.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { images.size })
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showAllPhotos by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (showAllPhotos) showAllPhotos = false else onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (dragOffset / 600f).coerceIn(0f, 0.45f))))
                .graphicsLayer {
                    translationY = dragOffset.coerceAtLeast(0f)
                    val scale = 1f - (dragOffset / 2000f).coerceIn(0f, 0.12f)
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !showAllPhotos,
            ) { page ->
                val src = images[page]
                val zoomableState = rememberZoomableState(
                    ZoomSpec(maxZoomFactor = 5f, minZoomFactor = 1f),
                )
                val zoomed = (zoomableState.zoomFraction ?: 0f) > 0.01f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!showAllPhotos && !zoomed) {
                                Modifier.pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (dragOffset > 140f) onDismissRequest()
                                            dragOffset = 0f
                                        },
                                        onDragCancel = { dragOffset = 0f },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            if (amount > 0f || dragOffset > 0f) {
                                                dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                                            }
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = remember(src, sourceOrigin) {
                        val file = ReadBook.book?.let { BookHelp.getImage(it, src) }
                        when {
                            file?.exists() == true -> file
                            else -> ImageRequest.Builder(context)
                                .data(src)
                                .crossfade(true)
                                .build()
                        }
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomable(
                                state = zoomableState,
                                gestures = EnabledZoomGestures.ZoomAndPan,
                            ),
                    )
                }
            }

            // SPEC-244/266: 分享 | 全部照片 | 保存 | 关闭 — 44dp 圆钮、图标 20dp、间距 4dp。
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GalleryChromeIconButton(
                    icon = Symbols.Share,
                    contentDescription = stringResource(R.string.share),
                    onClick = {
                        val src = images.getOrNull(pagerState.currentPage) ?: return@GalleryChromeIconButton
                        scope.launch { shareImage(context, src, sourceOrigin) }
                    },
                )
                GalleryChromeIconButton(
                    icon = Symbols.PhotoLibrary,
                    contentDescription = stringResource(R.string.reader_all_photos),
                    onClick = { showAllPhotos = true },
                )
                GalleryChromeIconButton(
                    icon = Symbols.Save,
                    contentDescription = stringResource(R.string.save_image),
                    onClick = {
                        val src = images.getOrNull(pagerState.currentPage) ?: return@GalleryChromeIconButton
                        scope.launch { saveImage(context, src, sourceOrigin) }
                    },
                )
                GalleryChromeIconButton(
                    icon = Symbols.Close,
                    contentDescription = stringResource(R.string.close),
                    onClick = {
                        if (showAllPhotos) showAllPhotos = false else onDismissRequest()
                    },
                )
            }

            if (!showAllPhotos) {
                Text(
                    text = "${pagerState.currentPage + 1}/${images.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (showAllPhotos) {
                GalleryAllPhotosGrid(
                    images = images,
                    selectedIndex = pagerState.currentPage,
                    sourceOrigin = sourceOrigin,
                    onSelect = { index ->
                        scope.launch {
                            pagerState.scrollToPage(index.coerceIn(0, images.lastIndex))
                            showAllPhotos = false
                        }
                    },
                    onClose = { showAllPhotos = false },
                )
            }
        }
    }
}

@Composable
private fun GalleryAllPhotosGrid(
    images: List<String>,
    selectedIndex: Int,
    sourceOrigin: String?,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(enabled = false, onClick = {}),
    ) {
        Text(
            text = stringResource(R.string.reader_all_photos),
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 18.dp),
        )
        GalleryChromeIconButton(
            icon = Symbols.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 72.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            itemsIndexed(images, key = { index, src -> "$index:$src" }) { index, src ->
                val model = remember(src, sourceOrigin) {
                    val file = ReadBook.book?.let { BookHelp.getImage(it, src) }
                    when {
                        file?.exists() == true -> file
                        else -> ImageRequest.Builder(context)
                            .data(src)
                            .crossfade(true)
                            .build()
                    }
                }
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        )
                        .clickable(role = Role.Button) { onSelect(index) },
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryChromeIconButton(
    icon: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // SPEC-244: fixed 44dp visual (avoid M3 IconButton default ~48 slot).
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            size = 20.dp,
        )
    }
}

private suspend fun saveImage(context: android.content.Context, src: String, sourceOrigin: String?) {
    val bytes = withContext(Dispatchers.IO) { loadImageBytes(context, src, sourceOrigin) }
    val ok = bytes != null && saveImageToGallery(context, bytes)
    context.toastOnUi(
        if (ok) context.getString(R.string.save_success)
        else context.getString(R.string.error),
    )
}

private suspend fun shareImage(context: android.content.Context, src: String, sourceOrigin: String?) {
    val bytes = withContext(Dispatchers.IO) { loadImageBytes(context, src, sourceOrigin) }
    if (bytes == null) {
        // Fallback: share URL text.
        if (src.startsWith("http")) {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, src)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    context.getString(R.string.share),
                ),
            )
        } else {
            context.toastOnUi(R.string.error)
        }
        return
    }
    withContext(Dispatchers.IO) {
        val file = File(context.externalCacheDir, "share_img_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        file
    }.let { file ->
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                AppConst.authority,
                file,
            )
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    context.getString(R.string.share),
                ),
            )
        }.onFailure {
            context.toastOnUi(R.string.error)
        }
    }
}

private fun loadImageBytes(
    context: android.content.Context,
    src: String,
    @Suppress("UNUSED_PARAMETER") sourceOrigin: String?,
): ByteArray? {
    ImageProvider.get(src)?.let { bmp ->
        return ByteArrayOutputStream().use { stream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            stream.toByteArray()
        }
    }
    val file = ReadBook.book?.let { BookHelp.getImage(it, src) }
    if (file?.exists() == true) {
        return file.readBytes()
    }
    return runCatching {
        java.net.URL(src).openStream().use { it.readBytes() }
    }.getOrNull()
}
