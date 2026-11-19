package id.sysfile.manager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * WB4 Layar sambutan (r113, pilihannya): pager 2 halaman — perkenalan,
 * lalu izin. Muncul sekali sampai selesai/dilewati (pref "welcome_done").
 * Semua teks berbahasa Inggris di kedua mode bahasa (permintaannya).
 */
@Composable
fun WelcomeScreen(
    accessGranted: Boolean,
    onRequestAccess: () -> Unit,
    onFinish: () -> Unit
) {
    val t = txt()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            if (page == 0) WelcomeIntroPage()
            else WelcomeAccessPage(accessGranted, onRequestAccess)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(
                            width = if (i == pagerState.currentPage) 16.dp else 8.dp,
                            height = 8.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                if (pagerState.currentPage == 0) onFinish()
                else scope.launch { pagerState.animateScrollToPage(0) }
            }) {
                Text(if (pagerState.currentPage == 0) t.welcomeSkip else t.welcomeBack)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                if (pagerState.currentPage == 0) scope.launch { pagerState.animateScrollToPage(1) }
                else onFinish()
            }) {
                Text(if (pagerState.currentPage == 0) t.welcomeNext else t.welcomeStart)
            }
        }
    }
}

/** Halaman 1: logo + sapaan + kartu sorotan fitur. */
@Composable
private fun WelcomeIntroPage() {
    val t = txt()
    Column(
        Modifier.fillMaxSize().padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ubin logo M11 "SF" (sama seperti bilah atas Beranda + Tentang).
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SF",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp
            )
            Box(
                Modifier.size(width = 3.dp, height = 88.dp)
                    .rotate(30f)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
        Text(
            t.welcomeTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            t.welcomePitch,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFA78BFA).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Unarchive, null,
                        tint = Color(0xFFA78BFA), modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.welcomeFeatureTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        t.welcomeFeatureDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Halaman 2: kartu langkah izin + alasan. */
@Composable
private fun WelcomeAccessPage(
    accessGranted: Boolean,
    onRequestAccess: () -> Unit
) {
    val t = txt()
    Column(
        Modifier.fillMaxSize().padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        t.welcomeAccessTitle,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Chip status: ikut berubah saat pulang dari Pengaturan.
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(
                            1.dp,
                            if (accessGranted) Color(0xFF81C995) else Color(0xFFF5C542)
                        ),
                        color = Color.Transparent
                    ) {
                        Text(
                            if (accessGranted) t.welcomeGranted else t.welcomeNotGranted,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (accessGranted) Color(0xFF81C995) else Color(0xFFF5C542),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    t.welcomeAccessDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRequestAccess,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t.welcomeGrant) }
            }
        }
        Text(
            t.welcomeRationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
