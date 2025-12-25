package no.solver.solverapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import no.solver.solverapp.R
import no.solver.solverapp.ui.theme.SolverAppTheme

/**
 * Displays an object's icon from the API, with fallback to a default cube icon.
 * 
 * Icons are loaded from: {baseURL}/api/Resource/Icon/{objectTypeId}/Image
 * 
 * @param objectTypeId The type ID of the object (used to construct icon URL)
 * @param baseUrl The API base URL (e.g., "https://api365-demo.solver.no")
 * @param size The size of the icon (width and height)
 * @param tint Optional tint color for the fallback icon
 * @param modifier Modifier for the composable
 */
@Composable
fun ObjectIcon(
    objectTypeId: Int,
    baseUrl: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val iconUrl = "$baseUrl/api/Resource/Icon/$objectTypeId/Image"
    
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(iconUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier.size(size),
        loading = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size / 2),
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            DefaultObjectIcon(
                modifier = Modifier.size(size),
                tint = tint
            )
        }
    )
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
