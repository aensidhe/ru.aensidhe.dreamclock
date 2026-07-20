package ru.aensidhe.dreamclock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import ru.aensidhe.dreamclock.core.photos.CaptionLines
import ru.aensidhe.dreamclock.immich.RenderPairedPhoto
import ru.aensidhe.dreamclock.immich.RenderPhoto

@Composable
fun PhotoSlide(
    render: RenderPhoto,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        PhotoImage(render.previewUrl, render.placeholderUrl, imageLoader, Modifier.fillMaxSize())
        render.caption?.let { CaptionBlock(it, Modifier.align(Alignment.BottomEnd).padding(24.dp)) }
    }
}

@Composable
fun PairedPhotoSlide(
    render: RenderPairedPhoto,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        PhotoSlide(render.left, imageLoader, Modifier.weight(1f).fillMaxHeight())
        PhotoSlide(render.right, imageLoader, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun PhotoImage(
    previewUrl: String,
    placeholderUrl: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    Box(modifier) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(placeholderUrl).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = ImageRequest.Builder(context).data(previewUrl).crossfade(true).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CaptionBlock(
    lines: CaptionLines,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        lines.dateTime?.let { Text(it, color = Color.White, fontSize = 20.sp) }
        lines.location?.let { Text(it, color = Color.White, fontSize = 20.sp) }
    }
}
