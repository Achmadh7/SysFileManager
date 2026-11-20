package id.sysfile.manager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pembungkus tampilan dokumen Office (BC3): ekstrak teks di IO thread,
 *  lalu tampilkan lewat TextViewerScreen mode read-only (tanpa tombol
 *  edit/simpan — teks hasil ekstrak bukan isi berkas asli, jadi tidak
 *  boleh disimpan kembali). Gagal ekstrak -> pesan gagal baca. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfficeViewerScreen(path: String, onBack: () -> Unit, notify: (String) -> Unit, snackbarHostState: SnackbarHostState) {
    val t = txt()
    var text by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        text = null
        failed = false
        val result = withContext(Dispatchers.IO) {
            try {
                OfficeText.extract(File(path))
            } catch (e: Exception) {
                null
            }
        }
        if (result == null) failed = true else text = result
    }
    val content = text
    BackHandler { onBack() }
    if (content != null) {
        TextViewerScreen(path, onBack, notify, snackbarHostState, extractedText = content)
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(File(path).name, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, t.home) }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (failed) Text(t.readFailed, Modifier.align(Alignment.Center))
                else LoadingIndicator(Modifier.align(Alignment.Center).size(52.dp))
            }
        }
    }
}
