package com.gamerx.downloader.ui.downloads

import android.content.Intent
import java.io.File

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.gamerx.downloader.data.db.entity.DownloadEntity
import com.gamerx.downloader.ui.components.DownloadCard
import com.gamerx.downloader.ui.components.EmptyStateView
import com.gamerx.downloader.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val queuedDownloads by viewModel.queuedDownloads.collectAsStateWithLifecycle()
    val completedDownloads by viewModel.completedDownloads.collectAsStateWithLifecycle()
    val cancelledDownloads by viewModel.cancelledDownloads.collectAsStateWithLifecycle()
    val erroredDownloads by viewModel.erroredDownloads.collectAsStateWithLifecycle()

    val tabs = listOf(
        TabInfo("Active", activeDownloads.size, Icons.Filled.PlayCircle),
        TabInfo("Queued", queuedDownloads.size, Icons.Filled.Queue),
        TabInfo("Completed", completedDownloads.size, Icons.Filled.CheckCircle),
        TabInfo("Cancelled", cancelledDownloads.size, Icons.Filled.Cancel),
        TabInfo("Errored", erroredDownloads.size, Icons.Filled.Error),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        // Tabs
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
        val scope = rememberCoroutineScope()

        LaunchedEffect(state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }

        LaunchedEffect(pagerState.currentPage) {
            if (state.selectedTab != pagerState.currentPage) {
                viewModel.selectTab(pagerState.currentPage)
            }
        }

        ScrollableTabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (state.selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            },
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = tab.title,
                                fontWeight = if (state.selectedTab == index)
                                    FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (tab.count > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (state.selectedTab == index)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "${tab.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (state.selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Tab content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val list = when (page) {
                0 -> activeDownloads
                1 -> queuedDownloads
                2 -> completedDownloads
                3 -> cancelledDownloads
                4 -> erroredDownloads
                else -> emptyList()
            }

            if (list.isEmpty()) {
                val (emptyIcon, emptyTitle, emptySubtitle) = when (page) {
                    0 -> Triple(
                        Icons.Outlined.CloudDownload,
                        "No active downloads",
                        "Downloads in progress will appear here"
                    )
                    1 -> Triple(
                        Icons.Outlined.Queue,
                        "Queue is empty",
                        "Queued downloads will appear here"
                    )
                    2 -> Triple(
                        Icons.Outlined.CheckCircle,
                        "No completed downloads",
                        "Successfully downloaded files will appear here"
                    )
                    3 -> Triple(
                        Icons.Outlined.Cancel,
                        "No cancelled downloads",
                        "Cancelled downloads will appear here"
                    )
                    else -> Triple(
                        Icons.Outlined.ErrorOutline,
                        "No errors",
                        "Failed downloads will appear here"
                    )
                }

                EmptyStateView(
                    icon = {
                        Icon(
                            emptyIcon,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        )
                    },
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // Clear all button for clearable tabs
                    if (page in listOf(2, 3, 4) && list.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        when (page) {
                                            2 -> viewModel.clearCompleted()
                                            3 -> viewModel.clearCancelled()
                                            4 -> viewModel.clearErrored()
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = NeonRed.copy(alpha = 0.7f),
                                    ),
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    items(
                        items = list,
                        key = { it.id },
                    ) { download ->
                        val context = LocalContext.current
                        DownloadCard(
                            item = download,
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onDelete = { viewModel.deleteDownload(download.id) },
                            onOpen = {
                                val path = download.filePath ?: return@DownloadCard
                                val file = File(path)
                                if (file.exists()) {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val mime = if (path.endsWith(".mp3") || path.endsWith(".m4a")
                                            || path.endsWith(".opus") || path.endsWith(".wav")) "audio/*" else "video/*"
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                            },
                            onShare = {
                                val path = download.filePath ?: return@DownloadCard
                                val file = File(path)
                                if (file.exists()) {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share"))
                                    } catch (_: Exception) { }
                                }
                            },
                            modifier = Modifier.animateItemPlacement(),
                        )
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

private data class TabInfo(
    val title: String,
    val count: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
