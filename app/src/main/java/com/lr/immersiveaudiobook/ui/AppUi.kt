package com.lr.immersiveaudiobook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lr.immersiveaudiobook.AppTab
import com.lr.immersiveaudiobook.ImportUiState
import com.lr.immersiveaudiobook.MainViewModel
import com.lr.immersiveaudiobook.data.local.CharacterCount
import com.lr.immersiveaudiobook.data.local.ChapterEntity
import com.lr.immersiveaudiobook.data.local.ImportState
import com.lr.immersiveaudiobook.data.local.NovelEntity
import com.lr.immersiveaudiobook.data.local.SentenceEntity
import com.lr.immersiveaudiobook.data.settings.AppSettings
import com.lr.immersiveaudiobook.playback.PlaybackUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val tabIcons: Map<AppTab, ImageVector> = mapOf(
    AppTab.SHELF to Icons.Default.AutoStories,
    AppTab.PLAYER to Icons.Default.Headphones,
    AppTab.CHARACTERS to Icons.Default.Groups,
    AppTab.SETTINGS to Icons.Default.Settings
)

@Composable
fun LrAudiobookApp(viewModel: MainViewModel) {
    val tab by viewModel.selectedTab.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 760.dp
        val expandedPlayer = maxWidth >= 1040.dp
        if (tablet) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                NavigationRail(
                    header = {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 20.dp).size(32.dp)
                        )
                    }
                ) {
                    AppTab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = item == tab,
                            onClick = { viewModel.selectedTab.value = item },
                            icon = { Icon(tabIcons.getValue(item), item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
                VerticalDivider()
                AppTabContent(viewModel, tab, expandedPlayer = expandedPlayer)
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == tab,
                                onClick = { viewModel.selectedTab.value = item },
                                icon = { Icon(tabIcons.getValue(item), item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AppTabContent(viewModel, tab, expandedPlayer = false)
                }
            }
        }
    }
}

@Composable
private fun AppTabContent(
    viewModel: MainViewModel,
    tab: AppTab,
    expandedPlayer: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            AppTab.SHELF -> ShelfScreen(viewModel)
            AppTab.PLAYER -> PlayerScreen(viewModel, expandedPlayer)
            AppTab.CHARACTERS -> CharacterScreen(viewModel)
            AppTab.SETTINGS -> SettingsScreen(viewModel)
        }
    }
}

@Composable
private fun ShelfScreen(viewModel: MainViewModel) {
    val novels by viewModel.novels.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var manualDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<NovelEntity?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importUri)
    }

    LaunchedEffect(importState) {
        when (val state = importState) {
            is ImportUiState.Done -> {
                snackbar.showSnackbar(state.message)
                viewModel.dismissImportMessage()
            }
            is ImportUiState.Error -> {
                snackbar.showSnackbar(state.message)
                viewModel.dismissImportMessage()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LR-沉浸式有声小说", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${novels.size} 本 · 本地优先",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            launcher.launch(
                                arrayOf("text/plain", "application/zip", "application/octet-stream")
                            )
                        }
                    ) {
                        Icon(Icons.Default.UploadFile, "导入 TXT 或 ZIP")
                    }
                    IconButton(onClick = { manualDialog = true }) {
                        Icon(Icons.Default.Add, "手动创建")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcher.launch(
                        arrayOf("text/plain", "application/zip", "application/octet-stream")
                    )
                }
            ) {
                Icon(Icons.Default.FolderOpen, "选择小说")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (importState is ImportUiState.Running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    (importState as ImportUiState.Running).label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (novels.isEmpty() && importState !is ImportUiState.Running) {
                EmptyShelf(
                    onImport = {
                        launcher.launch(
                            arrayOf("text/plain", "application/zip", "application/octet-stream")
                        )
                    },
                    onCreate = { manualDialog = true }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(novels, key = { it.id }) { novel ->
                        NovelCard(
                            novel = novel,
                            onClick = { viewModel.selectNovel(novel.id) },
                            onDelete = { deleteTarget = novel }
                        )
                    }
                }
            }
        }
    }

    if (manualDialog) {
        ManualNovelDialog(
            onDismiss = { manualDialog = false },
            onConfirm = { title, body ->
                manualDialog = false
                viewModel.createNovel(title, body)
            }
        )
    }
    deleteTarget?.let { novel ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除《${novel.title}》？") },
            text = { Text("将删除本地书库数据和应用私有副本，原始 TXT 不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNovel(novel)
                        deleteTarget = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyShelf(onImport: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(76.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text("把你的小说放进书架", style = MaterialTheme.typography.headlineSmall)
        Text(
            "支持 TXT、ZIP、UTF-8、GBK/GB2312；原始文件不会被修改。",
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("选择文件")
            }
            OutlinedButton(onClick = onCreate) {
                Icon(Icons.Default.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("粘贴文字")
            }
        }
    }
}

@Composable
private fun NovelCard(novel: NovelEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
        ) {
            Icon(
                Icons.Default.Headphones,
                null,
                modifier = Modifier.align(Alignment.Center).size(62.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
            if (novel.isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    null,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                novel.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${novel.chapterCount} 章 · ${novel.sourceEncoding}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { novel.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                when (novel.importState) {
                    ImportState.IMPORTING -> "解析中"
                    ImportState.ERROR -> "导入失败"
                    ImportState.READY -> "已听 ${(novel.progress * 100).roundToInt()}%"
                },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ManualNovelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动创建小说") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("小说名称（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("粘贴或输入正文") },
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(enabled = body.isNotBlank(), onClick = { onConfirm(title, body) }) {
                Text("创建")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PlayerScreen(viewModel: MainViewModel, expanded: Boolean) {
    val novel by viewModel.selectedNovel.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val sentences by viewModel.sentences.collectAsState()
    val selectedChapterId by viewModel.selectedChapterId.collectAsState()
    val selectedSentenceId by viewModel.selectedSentenceId.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

    if (novel == null) {
        EmptyPlayer { viewModel.selectedTab.value = AppTab.SHELF }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(novel?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        chapters.firstOrNull { it.id == selectedChapterId }?.title ?: "选择章节",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Default.Search, "全文搜索")
                }
                IconButton(onClick = { viewModel.selectedTab.value = AppTab.SETTINGS }) {
                    Icon(Icons.Default.Tune, "设置")
                }
            }
        )
        if (expanded) {
            Row(Modifier.fillMaxSize()) {
                ChapterPane(
                    chapters,
                    selectedChapterId,
                    onSelect = viewModel::selectChapter,
                    modifier = Modifier.width(260.dp).fillMaxHeight()
                )
                VerticalDivider()
                SentencePane(
                    sentences = sentences,
                    currentSentenceId = playback.sentenceId ?: selectedSentenceId,
                    settings = settings,
                    onPlay = viewModel::playSentence,
                    onEdit = { sentence, character, emotion ->
                        viewModel.updateSentence(sentence, character, emotion)
                    },
                    onBookmark = viewModel::addBookmark,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                VerticalDivider()
                VoiceAndPlaybackPane(
                    novel = novel!!,
                    playback = playback,
                    viewModel = viewModel,
                    modifier = Modifier.width(330.dp).fillMaxHeight()
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chapters, key = { it.id }) { chapter ->
                    FilterChip(
                        selected = chapter.id == selectedChapterId,
                        onClick = { viewModel.selectChapter(chapter.id) },
                        label = {
                            Text(
                                chapter.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                        }
                    )
                }
            }
            SentencePane(
                sentences = sentences,
                currentSentenceId = playback.sentenceId ?: selectedSentenceId,
                settings = settings,
                onPlay = viewModel::playSentence,
                onEdit = { sentence, character, emotion ->
                    viewModel.updateSentence(sentence, character, emotion)
                },
                onBookmark = viewModel::addBookmark,
                modifier = Modifier.weight(1f)
            )
            CompactControls(playback, viewModel)
        }
    }
    if (searchOpen) {
        SearchDialog(viewModel, onDismiss = { searchOpen = false })
    }
}

@Composable
private fun EmptyPlayer(openShelf: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Headphones, null, Modifier.size(72.dp))
        Text("还没有可播放的小说", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = openShelf, modifier = Modifier.padding(top = 16.dp)) {
            Text("返回书架")
        }
    }
}

@Composable
private fun ChapterPane(
    chapters: List<ChapterEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            "章节",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(chapters, key = { it.id }) { chapter ->
                ListItem(
                    headlineContent = {
                        Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("${chapter.characterCount} 字") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (chapter.id == selectedId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(chapter.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentencePane(
    sentences: List<SentenceEntity>,
    currentSentenceId: Long?,
    settings: AppSettings,
    onPlay: (SentenceEntity) -> Unit,
    onEdit: (SentenceEntity, String, String) -> Unit,
    onBookmark: (SentenceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var editTarget by remember { mutableStateOf<SentenceEntity?>(null) }
    LaunchedEffect(currentSentenceId, sentences, settings.autoScroll) {
        if (settings.autoScroll) {
            val index = sentences.indexOfFirst { it.id == currentSentenceId }
            if (index >= 0) listState.animateScrollToItem(index, -90)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        itemsIndexed(sentences, key = { _, item -> item.id }) { _, sentence ->
            val active = sentence.id == currentSentenceId
            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                shape = MaterialTheme.shapes.medium,
                tonalElevation = if (active) 2.dp else 0.dp,
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onPlay(sentence) },
                    onLongClick = { editTarget = sentence }
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (active) {
                        Icon(
                            if (currentSentenceId == sentence.id) Icons.Default.PlayArrow else Icons.Default.Headphones,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (sentence.isDialogue || sentence.emotion != "平静") {
                            Text(
                                "${sentence.characterName} · ${sentence.emotion}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            sentence.displayText,
                            fontSize = settings.fontSize.sp,
                            lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { onBookmark(sentence) }, Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            "书签",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    editTarget?.let { sentence ->
        SentenceEditDialog(
            sentence = sentence,
            onDismiss = { editTarget = null },
            onConfirm = { character, emotion ->
                onEdit(sentence, character, emotion)
                editTarget = null
            }
        )
    }
}

@Composable
private fun SentenceEditDialog(
    sentence: SentenceEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val emotions = listOf(
        "平静", "疑惑", "紧张", "恐惧", "愤怒", "悲伤", "激动",
        "神秘", "压迫", "轻松", "幽默", "急促", "低声耳语", "大声喊叫"
    )
    var character by remember(sentence.id) { mutableStateOf(sentence.characterName) }
    var emotion by remember(sentence.id) { mutableStateOf(sentence.emotion) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整这句话") },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        sentence.displayText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    OutlinedTextField(
                        value = character,
                        onValueChange = { character = it },
                        label = { Text("角色") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Text("情绪", fontWeight = FontWeight.SemiBold) }
                items(emotions) { item ->
                    FilterChip(
                        selected = emotion == item,
                        onClick = { emotion = item },
                        label = { Text(item) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(character, emotion) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CompactControls(playback: PlaybackUiState, viewModel: MainViewModel) {
    var timerDialog by remember { mutableStateOf(false) }
    Surface(tonalElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (playback.currentText.isNotBlank()) {
                Text(
                    "${playback.characterName} · ${playback.emotion}　${playback.currentText}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.Default.SkipPrevious, "上一句")
                }
                FilledIconButton(
                    onClick = viewModel::togglePlayback,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playback.isPlaying) "暂停" else "播放"
                    )
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.Default.SkipNext, "下一句")
                }
                IconButton(onClick = { timerDialog = true }) {
                    Icon(Icons.Default.Timer, "睡眠定时")
                }
                IconButton(onClick = viewModel::stop) {
                    Icon(Icons.Default.Stop, "停止")
                }
            }
        }
    }
    if (timerDialog) {
        SleepTimerDialog(
            playback.sleepRemainingMs,
            onDismiss = { timerDialog = false },
            onMinutes = {
                viewModel.setSleepTimer(it)
                timerDialog = false
            },
            onChapter = {
                viewModel.stopAfterChapter()
                timerDialog = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                timerDialog = false
            }
        )
    }
}

@Composable
private fun VoiceAndPlaybackPane(
    novel: NovelEntity,
    playback: PlaybackUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var timerDialog by remember { mutableStateOf(false) }
    LazyColumn(
        modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("播放控制", style = MaterialTheme.typography.titleLarge)
            Text(
                if (playback.currentText.isBlank()) "选择一句开始朗读" else
                    "${playback.characterName} · ${playback.emotion}",
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.Default.SkipPrevious, "上一句")
                }
                FilledIconButton(onClick = viewModel::togglePlayback, Modifier.size(58.dp)) {
                    Icon(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null
                    )
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.Default.SkipNext, "下一句")
                }
                IconButton(onClick = viewModel::stop) {
                    Icon(Icons.Default.Stop, "停止")
                }
            }
        }
        item {
            OutlinedButton(onClick = { timerDialog = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Timer, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    playback.sleepRemainingMs?.let { "剩余 ${formatDuration(it)}" } ?: "睡眠定时"
                )
            }
        }
        item { HorizontalDivider() }
        item { Text("系统语音参数", style = MaterialTheme.typography.titleMedium) }
        item {
            VoiceSlider("语速", novel.speechRate, 0.5f..1.6f) {
                viewModel.updateVoice(rate = it)
            }
        }
        item {
            VoiceSlider("音调 / 低沉度", novel.pitch, 0.55f..1.45f) {
                viewModel.updateVoice(pitch = it)
            }
        }
        item {
            VoiceSlider("音量", novel.volume, 0f..1f) {
                viewModel.updateVoice(volume = it)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("原创低沉悬疑", fontWeight = FontWeight.SemiBold)
                    Text(
                        "当前使用设备合法安装的系统中文语音，通过低音调、情绪变速和角色差异塑造氛围。沙哑、气声、混响和环境声已保留在 TTS 接口层，需接入合法授权的高质量引擎后启用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (timerDialog) {
        SleepTimerDialog(
            playback.sleepRemainingMs,
            onDismiss = { timerDialog = false },
            onMinutes = {
                viewModel.setSleepTimer(it)
                timerDialog = false
            },
            onChapter = {
                viewModel.stopAfterChapter()
                timerDialog = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                timerDialog = false
            }
        )
    }
}

@Composable
private fun VoiceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("%.2f".format(value), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long?,
    onDismiss: () -> Unit,
    onMinutes: (Int) -> Unit,
    onChapter: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val options = listOf(10, 15, 20, 30, 45, 60, 90, 120)
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时") },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (remainingMs != null) {
                    item {
                        Text(
                            "当前剩余 ${formatDuration(remainingMs)}，最后一分钟逐步降低后续句子的音量。",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(options) { minutes ->
                    OutlinedButton(
                        onClick = { onMinutes(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("$minutes 分钟") }
                }
                item {
                    OutlinedButton(onClick = onChapter, Modifier.fillMaxWidth()) {
                        Text("当前章节结束后停止")
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                            label = { Text("自定义分钟") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = custom.toIntOrNull()?.let { it > 0 } == true,
                            onClick = { custom.toIntOrNull()?.let(onMinutes) }
                        ) { Text("开始") }
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs != null) {
                TextButton(onClick = onCancelTimer) { Text("取消定时") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun SearchDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全文搜索") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::search,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Default.Close, "清空")
                            }
                        }
                    },
                    label = { Text("关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(results, key = { it.id }) { sentence ->
                        ListItem(
                            headlineContent = {
                                Text(sentence.displayText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${sentence.characterName} · ${sentence.emotion}")
                            },
                            modifier = Modifier.clickable {
                                viewModel.jumpToSearchResult(sentence)
                                onDismiss()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun CharacterScreen(viewModel: MainViewModel) {
    val novel by viewModel.selectedNovel.collectAsState()
    val counts by viewModel.characterCounts.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("角色管理")
                    Text(
                        novel?.title ?: "请先选择小说",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        )
        if (novel == null) {
            EmptyPlayer { viewModel.selectedTab.value = AppTab.SHELF }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    "角色由对白上下文自动识别。长按播放器中的句子可立即修正角色和情绪；同名角色会保持一致的基础音高。",
                    modifier = Modifier.padding(14.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(counts, key = { it.characterName }) { role ->
                    CharacterRow(role)
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun CharacterRow(role: CharacterCount) {
    ListItem(
        leadingContent = {
            Surface(
                color = if (role.characterName == "旁白") {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (role.characterName == "旁白") Icons.Default.Headphones else Icons.Default.Person,
                        null
                    )
                }
            }
        },
        headlineContent = { Text(role.characterName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("${role.count} 句台词") },
        trailingContent = {
            AssistChip(
                onClick = { },
                label = { Text(if (role.characterName == "旁白") "低沉男声" else "自动音色") }
            )
        }
    )
}

@Composable
private fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    var cacheSize by remember { mutableLongStateOf(viewModel.cacheSizeBytes()) }
    var cacheRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(cacheRefresh) {
        if (cacheRefresh > 0) {
            delay(250)
            cacheSize = viewModel.cacheSizeBytes()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("设置") })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { SectionTitle("外观与正文") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "SYSTEM" to "跟随系统",
                        "DARK" to "深色",
                        "LIGHT" to "浅色"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = settings.themeMode == key,
                            onClick = { viewModel.setTheme(key) },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    when (key) {
                                        "DARK" -> Icons.Default.DarkMode
                                        "LIGHT" -> Icons.Default.LightMode
                                        else -> Icons.Default.Settings
                                    },
                                    null,
                                    Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
            item {
                SettingsSlider(
                    "正文字号",
                    settings.fontSize.toFloat(),
                    14f..32f,
                    "${settings.fontSize} sp"
                ) { viewModel.setFontSize(it.roundToInt()) }
            }
            item {
                SettingsSlider(
                    "行距",
                    settings.lineSpacing,
                    1.1f..2f,
                    "%.2f".format(settings.lineSpacing)
                ) { viewModel.setLineSpacing(it) }
            }
            item {
                SettingsSwitch(
                    "正文自动跟随",
                    "朗读时让当前句自动滚动到可见位置",
                    settings.autoScroll,
                    viewModel::setAutoScroll
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle("播放与缓存") }
            item {
                SettingsSwitch(
                    "音频焦点恢复后继续",
                    "电话、闹钟等打断结束后自动恢复；默认关闭以免突然出声",
                    settings.resumeAfterInterruption,
                    viewModel::setResumeAfterInterruption
                )
            }
            item {
                SettingsSwitch(
                    "仅 Wi-Fi 自动缓存",
                    "对后续接入的高质量 TTS 生成任务生效",
                    settings.wifiOnlyCache,
                    viewModel::setWifiOnlyCache
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.FolderOpen, null) },
                    headlineContent = { Text("音频缓存") },
                    supportingContent = { Text(formatBytes(cacheSize)) },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                viewModel.clearCache()
                                cacheRefresh++
                            }
                        ) { Text("清理") }
                    }
                )
            }
            item { HorizontalDivider() }
            item { SectionTitle("隐私与版权") }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("本地优先", fontWeight = FontWeight.SemiBold)
                        Text(
                            "TXT、阅读记录、角色和设置默认保存在本机。当前版本不会上传小说全文，也没有硬编码任何云端密钥。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "软件不包含周建龙姓名、声纹、录音、肖像或商业小说。导出音频前，用户应确认拥有文字与声音的版权或授权。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item {
                Text(
                    "LR-沉浸式有声小说 1.0.0\nAndroid 8.0+ · Kotlin · Compose · Room",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onCheckedChange) }
    )
}

@Composable
private fun SettingsSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value.coerceIn(range), onValueChange, valueRange = range)
    }
}

@Composable
private fun VerticalDivider() {
    Divider(Modifier.fillMaxHeight().width(1.dp))
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
