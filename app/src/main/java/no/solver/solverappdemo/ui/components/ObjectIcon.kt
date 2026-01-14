package no.solver.solverappdemo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import no.solver.solverappdemo.R
import no.solver.solverappdemo.ui.theme.SolverAppTheme

/**
 * Displays an object's icon, using cached bitmap if available,
 * otherwise falling back to network load with Coil.
 * 
 * Performance optimizations:
 * - Uses pre-cached bitmaps from IconCacheManager when available (no network, no loading state)
 * - Falls back to AsyncImage (not SubcomposeAsyncImage) for uncached icons
 * - Disables crossfade to reduce animation overhead during scroll
 * - Uses aggressive disk caching via Coil
 * 
 * @param objectTypeId The type ID of the object (used to construct icon URL)
 * @param baseUrl The API base URL (e.g., "https://api365-demo.solver.no")
 * @param cachedBitmap Pre-cached bitmap from IconCacheManager (null if not cached)
 * @param size The size of the icon (width and height)
 * @param tint Optional tint color for the fallback icon
 * @param modifier Modifier for the composable
 */
@Composable
fun ObjectIcon(
    objectTypeId: Int,
    baseUrl: String,
    modifier: Modifier = Modifier,
    cachedBitmap: Bitmap? = null,
    size: Dp = 32.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    when {
        // Use pre-cached bitmap if available - fastest path, no recomposition
        cachedBitmap != null -> {
            Image(
                bitmap = cachedBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.size(size),
                contentScale = ContentScale.Fit
            )
        }
        // Fallback to network loading with optimized AsyncImage
        else -> {
            val iconUrl = "$baseUrl/api/Resource/Icon/$objectTypeId/Image"
            val context = LocalContext.current
            
            // Remember the ImageRequest to avoid recreating on every recomposition
            val imageRequest = remember(iconUrl) {
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .crossfade(false) // Disable crossfade for smoother scrolling
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = modifier.size(size),
                placeholder = painterResource(id = R.drawable.ic_cube),
                error = painterResource(id = R.drawable.ic_cube),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Simplified ObjectIcon that only uses cached bitmaps.
 * Use this in performance-critical scrolling lists.
 */
@Composable
fun CachedObjectIcon(
    cachedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    if (cachedBitmap != null) {
        Image(
            bitmap = cachedBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        DefaultObjectIcon(
            modifier = modifier.size(size),
            tint = tint
        )
    }
}

@Composable
private fun DefaultObjectIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_cube),
        contentDescription = null,
        modifier = modifier,
        tint = tint
    )
}

@Preview(showBackground = true)
@Composable
private fun ObjectIconPreview() {
    SolverAppTheme {
        ObjectIcon(
            objectTypeId = 1,
            baseUrl = "https://api365-demo.solver.no",
            size = 48.dp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DefaultObjectIconPreview() {
    SolverAppTheme {
        DefaultObjectIcon(
            modifier = Modifier.size(48.dp)
        )
    }
}
