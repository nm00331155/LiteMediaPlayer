修正1: 全画面モード（没入モード → 真のフルスクリーン）
# プレイヤーの全画面表示を修正してください

## 現状の問題
没入モード(Immersive Mode)だけでは不十分。
横持ち時に画面いっぱいに動画を表示したい。

## 要件

### 画面制御 (PlayerActivity.kt または PlayerScreen.kt)
プレイヤー画面に入った時点で以下を全て適用する:

1. **強制横画面固定**
   requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
   （センサーで180°回転は許可、縦には戻さない）

2. **システムバー完全非表示**
   WindowCompat.setDecorFitsSystemWindows(window, false) を設定し、
   WindowInsetsControllerCompat で以下を実行:
   ```kotlin
   val controller = WindowCompat.getInsetsController(window, window.decorView)
   controller.apply {
       hide(WindowInsetsCompat.Type.systemBars())
       systemBarsBehavior =
           WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
   }
ノッチ/カットアウト領域にも描画

Copyif (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
画面常時ON

Copywindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
プレイヤー画面を離脱した時に全て元に戻す DisposableEffect 内で復帰処理:

CopyDisposableEffect(Unit) {
    // ... 上記の全画面設定を適用
    onDispose {
        controller.show(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
ExoPlayer の SurfaceView 設定
PlayerView の resizeMode を AspectRatioFrameLayout.RESIZE_MODE_FIT に設定 （アスペクト比を保ちつつ画面最大化）
設定で RESIZE_MODE_ZOOM（画面を埋める）も選択可能にする
コントロールUI
コントロール（シークバー、ボタン類）はオーバーレイで表示
3秒無操作で自動非表示
画面中央タップで表示/非表示トグル
コントロール表示中もシステムバーは出さない

---

## 修正2: 動画一覧をサムネイル+名称表示

動画ファイル一覧画面をサムネイル付きリストに変更してください
現状の問題
ファイル名だけのリスト表示で見づらい。

要件
サムネイル取得 (VideoThumbnailLoader.kt を新規作成)
Copyclass VideoThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val thumbnailCache = LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 20).toInt() // maxMemの5%
    )

    suspend fun loadThumbnail(
        uri: Uri,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = uri.toString()
        thumbnailCache.get(key)?.let { return@withContext it }

        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : ThumbnailUtils を使用
            try {
                context.contentResolver.loadThumbnail(
                    uri, Size(width, height), null
                )
            } catch (e: Exception) { null }
        } else {
            // Android 9以下: MediaMetadataRetriever を使用
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(
                        1_000_000, // 1秒の位置
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )?.let { frame ->
                        Bitmap.createScaledBitmap(frame, width, height, true)
                    }
                }
            } catch (e: Exception) { null }
        }

        bitmap?.also { thumbnailCache.put(key, it) }
    }
}
Copy
一覧画面UI (VideoListScreen.kt を修正)
Copy@Composable
fun VideoListItem(
    video: VideoFile,
    thumbnail: Bitmap?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.height(90.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // サムネイル (16:9)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // ローディング or プレースホルダ
                    Icon(
                        Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // 動画時間をサムネイル右下に表示
                if (video.duration > 0) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // ファイル情報
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(video.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = video.resolution ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
Copy
ViewModel修正 (VideoListViewModel.kt)
サムネイルは LaunchedEffect で非同期読み込み
スクロール時にまだ表示されていないサムネイルはロードしない (LazyColumn の visibleItemsInfo を利用)
VideoFile data class に duration, size, resolution を追加:
Copydata class VideoFile(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val duration: Long,       // ms
    val resolution: String?,  // "1920x1080" など
    val dateModified: Long
)
ファイルスキャン時に MediaMetadataRetriever でメタデータも取得

---

## 修正3: フォルダ削除機能

登録済み動画フォルダの削除機能を実装してください
現状の問題
追加したフォルダを削除(登録解除)する手段がない。

要件
UIでの削除操作（2通り実装）
長押しで削除ダイアログ フォルダリスト内のフォルダアイテムを長押し → 確認ダイアログ「このフォルダの登録を解除しますか？ （フォルダ内のファイルは削除されません）」 → OK で削除

スワイプで削除 SwipeToDismissBox を使用し、左スワイプで削除 背景に赤色 + ゴミ箱アイコンを表示 スワイプ完了後に確認ダイアログ（誤操作防止）

実装
Copy// VideoFolderDao.kt に追加
@Dao
interface VideoFolderDao {
    // 既存
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: VideoFolder)

    @Query("SELECT * FROM video_folders ORDER BY addedDate DESC")
    fun getAllFolders(): Flow<List<VideoFolder>>

    // ★ 追加
    @Delete
    suspend fun deleteFolder(folder: VideoFolder)

    @Query("DELETE FROM video_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)
}
Copy// FolderListScreen.kt 内のフォルダアイテム
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderItem(
    folder: VideoFolder,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
                false  // ダイアログで確認するのでまだ消さない
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { showDeleteDialog = true }
                )
        ) {
            // フォルダ名、ファイル数 などの表示
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("フォルダ登録を解除") },
            text = { Text("「${folder.name}」の登録を解除しますか？\nフォルダ内のファイルは削除されません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("解除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
            }
        )
    }
}
Copy
SAFパーミッションの解放
フォルダ削除時に永続パーミッションも解放する:

Copy// ViewModel内
fun deleteFolder(folder: VideoFolder) {
    viewModelScope.launch {
        // SAFの永続パーミッションを解放
        try {
            context.contentResolver.releasePersistableUriPermission(
                folder.uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) { /* パーミッションが既に無い場合は無視 */ }

        folderDao.deleteFolder(folder)
    }
}

---

## 修正4: ローカルネットワーク接続（SMB/DLNA）

ローカルネットワーク接続機能を追加してください
概要
LAN内のNAS/PC/サーバーからファイルを参照・ストリーミング・ダウンロードする。

対応プロトコル
SMB (smbj ライブラリ使用)
build.gradle.kts に追加:

implementation("com.hierynomus:smbj:0.13.0")
HTTP/WebDAV
OkHttp ベースで実装（ExoPlayer も HTTP ストリーミング対応済み）

ネットワーク探索画面 (NetworkBrowserScreen.kt)
サーバー追加UI
Copydata class NetworkServer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 表示名
    val protocol: Protocol,      // SMB, HTTP, WEBDAV
    val host: String,            // IP or hostname
    val port: Int?,              // null=デフォルトポート
    val shareName: String?,      // SMB共有名
    val basePath: String?,       // ベースパス
    val username: String?,       // null=匿名
    val password: String?,       // EncryptedSharedPreferences で暗号化保存
    val addedDate: Long = System.currentTimeMillis()
)

enum class Protocol { SMB, HTTP, WEBDAV }
サーバー追加フォーム
プロトコル選択 (ドロップダウン)
ホスト名/IP入力
ポート (任意)
共有名 / パス入力
ユーザー名/パスワード (任意)
接続テストボタン (入力内容で接続を試行し成否を表示)
ファイルブラウザ
サーバー接続後、フォルダ/ファイルをリスト表示
動画ファイル、画像ファイル(コミック用)、ZIP/CBZ を表示
パンくずリストでパス表示
ファイル種別でアイコンを分ける
ストリーミング再生
SMBストリーミング
ExoPlayer のカスタム DataSource を実装:

Copyclass SmbDataSource(
    private val client: SMBClient,
    private val server: NetworkServer
) : BaseDataSource(/* isNetwork */ true) {

    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        // SMB接続してファイルのInputStreamを取得
        val connection = client.connect(server.host, server.port ?: 445)
        val session = connection.authenticate(
            AuthenticationContext(
                server.username ?: "",
                (server.password ?: "").toCharArray(),
                ""
            )
        )
        val share = session.connectShare(server.shareName) as DiskShare
        val file = share.openFile(
            dataSpec.uri.path,
            setOf(AccessMask.GENERIC_READ),
            null, SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        inputStream = file.inputStream
        bytesRemaining = file.fileInformation.standardInformation.endOfFile

        if (dataSpec.position > 0) {
            inputStream?.skip(dataSpec.position)
            bytesRemaining -= dataSpec.position
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        // inputStreamから読み取り
        return inputStream?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = null
    override fun close() { inputStream?.close() }
}
Copy
HTTPストリーミング
ExoPlayer のデフォルト DefaultHttpDataSource をそのまま使用可能。 Basic認証が必要な場合は DefaultHttpDataSource.Factory に setDefaultRequestProperties で Authorization ヘッダーを設定。

ダウンロード機能
DownloadManager (NetworkDownloadManager.kt)
Copyclass NetworkDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClientProvider: SmbClientProvider
) {
    data class DownloadTask(
        val id: String = UUID.randomUUID().toString(),
        val sourceUri: String,
        val server: NetworkServer,
        val destinationUri: Uri,
        val fileName: String,
        val totalBytes: Long,
        val downloadedBytes: Long = 0,
        val status: DownloadStatus = DownloadStatus.PENDING
    )

    enum class DownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads

    // Foreground Service で実行（通知にプログレス表示）
    // 同時ダウンロード数: 最大2（設定可能）
    // 一時停止/再開対応
    // WiFi切断時に自動一時停止（設定可能）
}
ダウンロード先
SAF でユーザーが指定したフォルダ
ダウンロード完了後、自動的にローカルの動画/コミックライブラリに追加
ダウンロードUI (DownloadListScreen.kt)
現在のダウンロード一覧
プログレスバー (%) + 転送速度表示
一時停止/再開/キャンセルボタン
完了済みファイルはタップで再生/閲覧
ナビゲーション変更
BottomNavigationを4タブに変更: 「プレイヤー」「コミック」「ネットワーク」「設定」
パーミッション
AndroidManifest.xml に追加:

Copy<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
ファイル一覧:
NetworkBrowserScreen.kt
NetworkServerDao.kt / NetworkServer.kt (Room)
SmbClientProvider.kt
SmbDataSource.kt / SmbDataSourceFactory.kt
NetworkDownloadManager.kt
DownloadService.kt (Foreground Service)
DownloadListScreen.kt

---

## 修正5: ロック機能の個別動作修正

ロック機能が個別のフォルダ/本棚に正しく適用されない問題を修正してください
現状の問題
ロック設定が全体にしか効かず、特定フォルダや特定本棚に 個別にロックをかけることができない。

原因と修正
データ構造の修正 (LockConfig.kt)
ロック対象を汎用的に管理するテーブル:

Copy@Entity(
    tableName = "lock_configs",
    indices = [Index(value = ["targetType", "targetId"], unique = true)]
)
data class LockConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: LockTargetType,  // APP_GLOBAL, VIDEO_FOLDER, COMIC_SHELF
    val targetId: Long?,             // null = グローバル, それ以外 = フォルダ/書籍ID
    val authMethod: AuthMethod,      // PIN, PATTERN, BIOMETRIC
    val pinHash: String?,
    val patternHash: String?,
    val autoLockMinutes: Int = 5,    // 再ロックまでの時間
    val isHidden: Boolean = false,   // 非表示モード
    val isEnabled: Boolean = true
)

enum class LockTargetType {
    APP_GLOBAL,
    VIDEO_FOLDER,
    COMIC_SHELF
}

enum class AuthMethod { PIN, PATTERN, BIOMETRIC }
LockManager.kt (新規作成 — ロック判定の一元管理)
Copy@Singleton
class LockManager @Inject constructor(
    private val lockConfigDao: LockConfigDao,
    private val cryptoUtil: CryptoUtil
) {
    // 最後に認証成功した時刻を対象ごとに保持
    private val lastUnlocked = mutableMapOf<String, Long>()

    /**
     * 指定対象がロックされているかを判定
     */
    suspend fun isLocked(targetType: LockTargetType, targetId: Long?): Boolean {
        val config = lockConfigDao.getConfig(targetType, targetId)
            ?: return false  // ロック設定がなければロックなし

        if (!config.isEnabled) return false

        val key = "${targetType}_${targetId}"
        val lastTime = lastUnlocked[key] ?: return true
        val elapsed = System.currentTimeMillis() - lastTime
        val autoLockMs = config.autoLockMinutes * 60_000L

        return if (config.autoLockMinutes <= 0) {
            false  // 「無制限」設定
        } else {
            elapsed > autoLockMs
        }
    }

    /**
     * 認証成功を記録
     */
    fun recordUnlock(targetType: LockTargetType, targetId: Long?) {
        val key = "${targetType}_${targetId}"
        lastUnlocked[key] = System.currentTimeMillis()
    }

    /**
     * 非表示フォルダの可視性判定
     */
    suspend fun isHidden(targetType: LockTargetType, targetId: Long?): Boolean {
        val config = lockConfigDao.getConfig(targetType, targetId) ?: return false
        return config.isHidden && config.isEnabled
    }

    /**
     * PIN検証
     */
    suspend fun verifyPin(
        targetType: LockTargetType,
        targetId: Long?,
        inputPin: String
    ): Boolean {
        val config = lockConfigDao.getConfig(targetType, targetId) ?: return true
        return cryptoUtil.verifyHash(inputPin, config.pinHash ?: return false)
    }
}
Copy
DAOの修正 (LockConfigDao.kt)
Copy@Dao
interface LockConfigDao {
    @Query("""
        SELECT * FROM lock_configs
        WHERE targetType = :targetType AND
              (targetId = :targetId OR (:targetId IS NULL AND targetId IS NULL))
        LIMIT 1
    """)
    suspend fun getConfig(targetType: LockTargetType, targetId: Long?): LockConfig?

    @Query("SELECT * FROM lock_configs WHERE targetType = :targetType")
    fun getConfigsByType(targetType: LockTargetType): Flow<List<LockConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: LockConfig)

    @Delete
    suspend fun deleteConfig(config: LockConfig)
}
フォルダ/本棚一覧画面への組み込み
各一覧画面で表示前にLockManagerで判定を行う:

Copy// VideoListViewModel.kt 内
val folders: StateFlow<List<FolderWithLockState>> = combine(
    folderDao.getAllFolders(),
    lockConfigDao.getConfigsByType(LockTargetType.VIDEO_FOLDER)
) { folders, locks ->
    folders.mapNotNull { folder ->
        val lockConfig = locks.find { it.targetId == folder.id }

        // 非表示かつロック中なら一覧から除外
        if (lockConfig?.isHidden == true && lockConfig.isEnabled) {
            if (!showHiddenFolders) return@mapNotNull null
        }

        FolderWithLockState(
            folder = folder,
            isLocked = lockManager.isLocked(LockTargetType.VIDEO_FOLDER, folder.id),
            isHidden = lockConfig?.isHidden ?: false
        )
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
ロック設定UI（各フォルダ/書籍の設定メニューに追加）
フォルダ長押しメニュー、または書籍の3ドットメニューに 「ロック設定」を追加:

ロック ON/OFF
認証方式選択
PIN/パターン設定
自動再ロック時間
非表示にする ON/OFF

---

## 修正6: 画面回転制御の修正

画面回転の設定が機能しない問題を修正してください
現状の問題
設定画面で画面回転を変更しても反映されない。

原因
ActivityのrequestedOrientationが設定値と連動していない。

修正
設定値の定義
Copyenum class RotationSetting(val displayName: String) {
    AUTO("自動回転"),
    LANDSCAPE("横画面固定"),
    PORTRAIT("縦画面固定"),
    SENSOR_LANDSCAPE("横画面（センサー回転あり）"),
    LOCKED("現在の向きで固定")
}
OrientationController.kt (新規作成)
Activityのライフサイクルと連動して回転を制御:

Copyclass OrientationController(
    private val activity: ComponentActivity,
    private val settingsRepository: SettingsRepository
) {
    fun observe() {
        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.rotationSetting.collect { setting ->
                    applyRotation(setting)
                }
            }
        }
    }

    fun applyRotation(setting: RotationSetting) {
        activity.requestedOrientation = when (setting) {
            RotationSetting.AUTO ->
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            RotationSetting.LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            RotationSetting.PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            RotationSetting.SENSOR_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationSetting.LOCKED ->
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }
}
MainActivityに組み込み
Copyclass MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画面回転制御を開始
        val orientationController = OrientationController(this, settingsRepository)
        orientationController.observe()

        setContent { /* ... */ }
    }
}
プレイヤー画面での上書き
プレイヤー画面は一般設定に関わらず強制横画面にする。 ただし設定に「プレイヤーの回転設定を一般設定に従う」オプションを追加:

Copy// PlayerScreen.kt
val activity = LocalContext.current as ComponentActivity
val playerRotation by viewModel.playerRotationSetting.collectAsState()

DisposableEffect(playerRotation) {
    val previousOrientation = activity.requestedOrientation

    if (playerRotation == PlayerRotation.FORCE_LANDSCAPE) {
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    // FOLLOW_GLOBAL の場合は何もしない（一般設定に従う）

    onDispose {
        // プレイヤーを離れたら元に戻す
        activity.requestedOrientation = previousOrientation
    }
}
設定画面のUI
Copy// 一般設定内
SettingsSection(title = "画面回転") {
    RadioGroup(
        options = RotationSetting.entries,
        selected = currentRotation,
        onSelect = { viewModel.setRotation(it) },
        label = { it.displayName }
    )
}

// プレイヤー設定内
SettingsSection(title = "プレイヤーの画面回転") {
    RadioGroup(
        options = listOf(
            PlayerRotation.FORCE_LANDSCAPE to "常に横画面",
            PlayerRotation.FOLLOW_GLOBAL to "一般設定に従う"
        ),
        selected = playerRotation,
        onSelect = { viewModel.setPlayerRotation(it) }
    )
}

---

## Copilotへの指示順序

修正の依存関係を考慮すると、以下の順番が効率的です。

**Step 1** → 画面回転の修正（他の全画面にも影響するため最初に）
**Step 2** → 全画面モード修正（回転制御の仕組みの上に構築）
**Step 3** → フォルダ削除（DAOの変更を含む、他への影響が小さい）
**Step 4** → サムネイル一覧（UI変更のみ、独立性が高い）
**Step 5** → ロック機能修正（DB構造の変更を含む、マイグレーション注意）
**Step 6** → ネットワーク機能（最も大きい追加機能、最後に）

各Stepが完了するたびにビルド・動作確認してからCopilotに次を指示してください。特にStep 5のRoom DBスキーマ変更ではマイグレーションコードの生成も忘れずに指示するようにしてください。