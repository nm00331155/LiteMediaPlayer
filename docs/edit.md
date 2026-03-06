全体アーキテクチャ（最初にCopilotに渡す設計書）
以下をプロジェクトルートに ARCHITECTURE.md として保存し、Copilotのコンテキストに含めてください。

Copy# プロジェクト: LiteMedia Player

## 技術スタック
- 言語: Kotlin
- 最小SDK: API 26 (Android 8.0)
- ビルド: Gradle (Kotlin DSL)
- 動画再生: Media3 (ExoPlayer)
- 画像表示: Coil (軽量)
- UI: Jetpack Compose + Material3
- DI: Hilt
- ローカルDB: Room (設定・ロック情報保存)
- メモリ管理: カスタム LifecycleObserver

## モジュール構成
app/ ├── core/ # 共通ユーティリティ, メモリ管理 ├── player/ # 動画プレイヤー機能 ├── comic/ # コミックビューア機能 ├── lock/ # ロック画面機能 ├── settings/ # 設定画面 └── data/ # Room DB, DataStore


## 設計原則
- シンプル・軽量を最優先
- 各機能モジュールは疎結合
- メモリ使用量を常時監視・制御
Phase 1: プロジェクト基盤
Copilotへの指示（チャットまたはインラインで）:

以下の仕様でAndroidプロジェクトの基盤を作成してください。

# プロジェクト基盤
- Kotlin + Jetpack Compose + Material3
- Hilt による DI セットアップ
- 最小SDK: API 26
- build.gradle.kts に以下の依存関係を含める:
  - androidx.media3:media3-exoplayer (最新安定版)
  - androidx.media3:media3-ui
  - io.coil-kt:coil-compose
  - androidx.room:room-runtime / room-compiler
  - androidx.datastore:datastore-preferences
  - androidx.hilt:hilt-navigation-compose

# ナビゲーション構造
- BottomNavigation で3タブ: 「プレイヤー」「コミック」「設定」
- NavHost + sealed class でルート管理

# MainActivityの要件
- EdgeToEdge表示
- システムバーの制御（没入モード切替可能）

まず build.gradle.kts、MainActivity.kt、Navigation.kt を生成してください。
Phase 2: 動画プレイヤー
# 動画プレイヤーモジュールを作成してください

## 基本機能
- Media3 (ExoPlayer) ベースのシンプルな動画プレイヤー
- 対応フォーマット: MP4, MKV, AVI, WebM (ExoPlayerデフォルト対応範囲)
- 再生/一時停止、シークバー、経過時間/残り時間表示
- 画面回転対応 (横画面フルスクリーン)
- バックグラウンド再生しない（画面離脱で一時停止）

## ジェスチャー操作 (最重要)
全てのジェスチャーは Modifier.pointerInput で実装:

1. **水平スワイプ（シーク）**
   - 右スワイプ → 前方へシーク
   - 左スワイプ → 後方へシーク
   - シーク間隔は設定で変更可能（デフォルト: 10秒）
   - スワイプ距離に応じてシーク量を変動させる
   - スワイプ中は画面中央にシーク量をオーバーレイ表示

2. **右端の垂直スワイプ（音量）**
   - 画面右側30%の領域で上下スワイプ
   - 上スワイプ → 音量UP / 下スワイプ → 音量DOWN
   - AudioManager.STREAM_MUSIC を制御
   - 音量インジケータをオーバーレイ表示

3. **左端の垂直スワイプ（明るさ）**
   - 画面左側30%の領域で上下スワイプ
   - 上スワイプ → 明るさUP / 下スワイプ → 明るさDOWN
   - WindowManager.LayoutParams.screenBrightness を制御
   - 明るさインジケータをオーバーレイ表示

4. **ダブルタップ**
   - 再生/一時停止トグル

## フォルダ指定機能
- SAF (Storage Access Framework) でフォルダ選択
- 選択フォルダ内の動画ファイルをリスト表示
- 複数フォルダを登録可能（Room で永続化）
- フォルダごとにリスト表示

## ファイル:
- PlayerScreen.kt (Compose画面)
- GestureOverlay.kt (ジェスチャー検出レイヤー)
- PlayerViewModel.kt
- VideoFolder.kt (Roomエンティティ)
- VideoFolderDao.kt

各ファイルを順番に生成してください。
Phase 3: コミックビューア
# コミックビューアモジュールを作成してください

## 基本機能
- ZIP/CBZ/CBR/フォルダ内の画像を読み込み
- 対応画像形式: JPEG, PNG, WebP
- Coil で画像読み込み（メモリキャッシュ制限付き）

## 閲覧モード（設定で切替可能）
1. **ページめくりモード**
   - 左右スワイプでページ送り
   - めくりアニメーション選択: スライド / カール / フェード / なし
   - アニメーション速度: 高速(150ms) / 標準(300ms) / 低速(500ms) / カスタム(ms指定)

2. **縦スクロールモード（ウェブトゥーン向け）**
   - 連続縦スクロール
   - スクロール速度調整可能
   - ページスナップ ON/OFF

3. **見開きモード**
   - 2ページ並列表示（横画面時）
   - 縦画面では自動で1ページに切替

## ページ送り方向
- 右→左（日本の漫画デフォルト）
- 左→右（海外コミック）
- 設定で切替可能

## 操作
- タップ領域でページ送り（左/中央/右の3分割、各領域の動作を設定可能）
- ピンチズームとダブルタップズーム
- ズーム倍率: 1.0x〜5.0x（最大値を設定可能）
- ズーム時のパン操作

## 本棚画面
- 登録フォルダをグリッド表示
- 各書籍のサムネイル（最初のページ）
- 既読/未読/途中 のステータス表示
- 最後に読んだページを記憶（Room保存）

## 表示設定
- 背景色: 黒/白/グレー/カスタム
- 余白(パディング)調整
- 明るさ独立設定（アプリ内明るさ）
- ブルーライトフィルター（オーバーレイでセピア/暖色フィルタ）

## ファイル:
- ComicShelfScreen.kt (本棚)
- ComicReaderScreen.kt (閲覧画面)
- ComicReaderViewModel.kt
- PageRenderer.kt (画像デコード・表示)
- ComicBook.kt / ComicBookDao.kt (Room)
- ComicSettings.kt (DataStore)

各ファイルを順番に生成してください。
Phase 4: ロック画面機能
# ロック画面機能を実装してください

## 要件
コンテンツ単位で細かくロックを設定可能にする。

## ロック対象（個別に設定可能）
1. **動画フォルダ単位** — 特定のフォルダにロック設定
2. **本棚単位** — 特定のコミックフォルダ/書籍にロック設定
3. **アプリ全体** — アプリ起動時にロック

## 認証方式（設定で選択）
- PINコード（4〜8桁、桁数を設定可能）
- パターンロック
- 生体認証（BiometricPrompt API）— 対応端末のみ

## UI/UX
- ロックされたコンテンツはサムネイルをぼかし表示 + 鍵アイコン
- ロック解除は対象コンテンツをタップした時のみ要求
- 認証成功後、一定時間（設定可能: 1分〜30分〜無制限）は再認証不要
- 偽装機能: ロックフォルダを「非表示」にするオプション（設定内の隠しジェスチャーで表示切替）

## データ保存
- PINはハッシュ化して Room に保存（平文保存禁止）
- パターンもハッシュ化保存
- EncryptedSharedPreferences を使用

## ファイル:
- LockScreen.kt (PIN/パターン入力UI)
- BiometricHelper.kt
- LockViewModel.kt
- LockConfig.kt / LockConfigDao.kt (Room)
- CryptoUtil.kt (ハッシュ化ユーティリティ)
Phase 5: メモリ管理
# メモリ管理モジュールを実装してください

## 要件
アプリのメモリ使用量を厳密に制御し、定期的に解放する。

## 実装内容

### 1. MemoryMonitor (LifecycleObserver)
- アプリのヒープ使用量をリアルタイム監視
- Runtime.getRuntime() で使用メモリを取得
- 閾値超過時に自動でクリーンアップ実行
- 閾値はアプリの maxMemory の70%（設定で変更可能）

### 2. 定期クリーンアップ
- WorkManager で定期実行（間隔は設定可能: 5分〜60分）
- クリーンアップ対象:
  - Coil の画像キャッシュ (ImageLoader.memoryCache.clear())
  - ExoPlayer のバッファ解放
  - 未使用の Bitmap のリサイクル
  - System.gc() の明示呼び出し（最終手段）

### 3. メモリ使用状況の可視化（設定画面内）
- 現在のヒープ使用量 / 最大ヒープ量をプログレスバーで表示
- 手動クリーンアップボタン
- 各モジュール別の概算使用量表示

### 4. 画像読み込みの最適化
- Coil の MemoryCache サイズ制限（maxMemory の 15%）
- コミック表示時: 現在ページ ± 2ページのみメモリ保持
- サムネイルは低解像度(200px幅)で読み込み

### 5. ComponentCallbacks2 対応
- onTrimMemory コールバックで段階的にリソース解放
- TRIM_MEMORY_RUNNING_LOW: キャッシュ50%解放
- TRIM_MEMORY_RUNNING_CRITICAL: キャッシュ全解放

## ファイル:
- MemoryMonitor.kt
- CleanupWorker.kt (WorkManager)
- MemoryDashboard.kt (設定画面内のUI)
- CoilConfig.kt (ImageLoader設定)
Phase 6: 設定画面
# 統合設定画面を作成してください

## 設定項目（DataStore で永続化）

### プレイヤー設定
- スワイプシーク間隔: 5秒 / 10秒 / 15秒 / 30秒 / カスタム
- 音量感度: 低 / 中 / 高
- 明るさ感度: 低 / 中 / 高
- デフォルト再生速度: 0.5x〜2.0x
- 画面回転: 自動 / 固定横 / 固定縦
- 再開時の動作: 最初から / 前回位置から

### コミック設定
- めくり方向: 右→左 / 左→右
- めくりアニメーション: スライド / カール / フェード / なし
- アニメーション速度: スライダー (100ms〜1000ms)
- 閲覧モード: ページ / 縦スクロール / 見開き
- タップ領域設定: 3分割の各領域の動作割当
- 背景色 / 余白 / ブルーライトフィルタ
- ズーム上限: 2.0x〜5.0x

### ロック設定
- 認証方式選択
- PIN変更
- 自動再ロック時間
- 隠しフォルダの表示切替

### メモリ設定
- メモリ閾値: スライダー (50%〜90%)
- クリーンアップ間隔: 5分 / 15分 / 30分 / 60分
- メモリダッシュボード表示

### 一般
- テーマ: ライト / ダーク / システム準拠
- 言語: 日本語 / English

## UI
- カテゴリごとに折りたたみセクション
- Material3 の設定コンポーネント使用
- 各設定項目に簡潔な説明文を表示
Copilotを効率よく使うためのTips
1. ファイル単位で指示する

Copilotは1ファイルずつ生成させるのが最も安定します。「次に GestureOverlay.kt を生成して」のように順番に進めてください。

2. 既存ファイルをコンテキストに含める

VSCodeで関連ファイルを開いた状態でCopilot Chatに指示すると、整合性のあるコードを生成しやすくなります。特にViewModel → Screen の連携部分では両方開いておくのが効果的です。

3. エラーが出たらそのまま貼る

ビルドエラーやランタイムエラーは、ログをそのままCopilotに貼って「このエラーを修正して」と指示するのが最速です。

4. 生成後のチェックリスト（各Phase完了時）

- [ ] ビルドが通るか
- [ ] 画面遷移が正常か
- [ ] 権限（READ_EXTERNAL_STORAGE等）の宣言漏れがないか
- [ ] ProGuard/R8 で難読化エラーが出ないか
この設計であれば、各Phaseごとに動作確認しながら段階的に構築できます。何か特定のPhaseをさらに詳細化したい、あるいは特定の機能について技術的な質問があれば、お気軽にどうぞ。

・ビューア側ですが、大きい1枚の画像だった場合に自動的に半分で分割する機能が欲しい。
（1ページ表示の場合）
これ、可能であれば余白部分は自動的に削る機能があったら最高ですが、そんな機能は無理ですよね
無理どころか、どちらも十分に実現可能な機能です。

余白の自動トリミングは、実はコミックビューアではわりと定番の機能で、画像のピクセルを走査して「白（または背景色に近い色）」の領域を検出し、Bitmapをクロップするだけです。完璧ではありませんが、実用上は十分に機能します。

では、この2機能をCopilotに指示するプロンプトを追加します。

Phase 3 追加: 自動分割 & 余白トリミング
以下をPhase 3のコミックビューアの指示に追加してください。

# コミックビューア追加機能: 自動ページ分割 & 余白トリミング

## 1. 見開き画像の自動分割（PageSplitter.kt）

### 判定ロジック
- 画像の幅と高さの比率(aspect ratio)で判定
- 幅/高さ > 1.3 の場合「見開き画像」と判断（閾値は設定可能）
- 1ページ表示モードの時のみ自動分割を適用

### 分割処理
- 見開き画像を左半分・右半分の2つの Bitmap に分割
- めくり方向が「右→左」(日本式)の場合: 右半分 → 左半分 の順で表示
- めくり方向が「左→右」(海外式)の場合: 左半分 → 右半分 の順で表示
- 分割位置はデフォルトで中央50%だが、微調整可能（±5%のオフセット設定）

### スマート分割（可能であれば）
- 単純な中央分割ではなく、中央付近の縦ラインで
  最も「白い（背景色に近い）」列を検出し、そこで分割する
- これにより綴じ部分のズレに対応できる
- 検出範囲: 画像幅の45%〜55%の範囲内で最適な分割線を探索

### メモリ配慮
- 元画像を保持せず、分割後は元Bitmapをrecycle
- BitmapRegionDecoder を使用して必要な半分だけデコードする
  （大きい画像でもメモリを半分に抑えられる）

## 実装コード概要 (PageSplitter.kt):

```kotlin
class PageSplitter @Inject constructor() {

    data class SplitResult(
        val pages: List<PageRegion>,  // 1枚 or 2枚
        val wasSplit: Boolean
    )

    data class PageRegion(
        val rect: Rect  // 元画像内の領域
    )

    fun analyze(
        imageWidth: Int,
        imageHeight: Int,
        splitThreshold: Float = 1.3f
    ): Boolean {
        return imageWidth.toFloat() / imageHeight > splitThreshold
    }

    fun findOptimalSplitLine(
        decoder: BitmapRegionDecoder,
        imageWidth: Int,
        imageHeight: Int
    ): Int {
        // 画像中央付近(45%〜55%)の縦ラインをサンプリングし、
        // 最もピクセル輝度の平均が高い（白に近い）列を分割線とする
        // サンプリングは全行ではなく等間隔(例:20行)で軽量化
        val scanStart = (imageWidth * 0.45f).toInt()
        val scanEnd = (imageWidth * 0.55f).toInt()
        // ... 各列の輝度平均を計算し、最大値の列を返す
        return optimalX
    }

    fun split(
        decoder: BitmapRegionDecoder,
        imageWidth: Int,
        imageHeight: Int,
        rtl: Boolean = true  // 日本式
    ): SplitResult {
        val splitX = findOptimalSplitLine(decoder, imageWidth, imageHeight)
        val leftRect = Rect(0, 0, splitX, imageHeight)
        val rightRect = Rect(splitX, 0, imageWidth, imageHeight)

        val pages = if (rtl) {
            listOf(PageRegion(rightRect), PageRegion(leftRect))
        } else {
            listOf(PageRegion(leftRect), PageRegion(rightRect))
        }
        return SplitResult(pages = pages, wasSplit = true)
    }
}
2. 余白の自動トリミング（MarginTrimmer.kt）
検出アルゴリズム
画像の四辺（上・下・左・右）から内側に向かってスキャン
各ラインのピクセルを調べ「背景色」かどうかを判定
非背景ピクセルが一定割合（デフォルト2%）を超えたラインで 「コンテンツの端」と判定
検出した上下左右の境界でクロップ領域を決定
背景色の判定
画像の四隅のピクセルの色を取得し、最頻色を「背景色」と推定
許容差(tolerance)を設けて近似色も背景色と見なす （デフォルト: RGB各チャンネルで±30）
白背景だけでなく黒背景や色付き背景にも対応
パフォーマンス最適化
全ピクセルスキャンは重いため、サンプリングで軽量化:
inSampleSize=4 で縮小した画像でスキャン
検出した座標を元画像のスケールに変換
処理は Dispatchers.Default で非同期実行
安全マージン
検出境界から内側に少しだけ余裕を残す（設定可能: 0〜20px）
トリミングしすぎてコンテンツが切れるのを防止
トリミング量が画像の80%以上になる場合は異常とみなしトリミングしない
実装コード概要 (MarginTrimmer.kt):
Copyclass MarginTrimmer @Inject constructor() {

    data class TrimBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun toRect() = Rect(left, top, right, bottom)
        fun isValid(imageWidth: Int, imageHeight: Int): Boolean {
            val trimmedArea = (right - left) * (bottom - top)
            val totalArea = imageWidth * imageHeight
            return trimmedArea > totalArea * 0.2  // 20%以上残っていること
        }
    }

    fun detectBounds(
        bitmap: Bitmap,
        tolerance: Int = 30,
        contentThreshold: Float = 0.02f,
        safetyMargin: Int = 2
    ): TrimBounds {
        val bgColor = detectBackgroundColor(bitmap)

        val top = scanFromTop(bitmap, bgColor, tolerance, contentThreshold)
        val bottom = scanFromBottom(bitmap, bgColor, tolerance, contentThreshold)
        val left = scanFromLeft(bitmap, bgColor, tolerance, contentThreshold)
        val right = scanFromRight(bitmap, bgColor, tolerance, contentThreshold)

        return TrimBounds(
            left = (left - safetyMargin).coerceAtLeast(0),
            top = (top - safetyMargin).coerceAtLeast(0),
            right = (right + safetyMargin).coerceAtMost(bitmap.width),
            bottom = (bottom + safetyMargin).coerceAtMost(bitmap.height)
        )
    }

    private fun detectBackgroundColor(bitmap: Bitmap): Int {
        // 四隅のピクセルから最頻色を推定
        val corners = listOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(bitmap.width - 1, 0),
            bitmap.getPixel(0, bitmap.height - 1),
            bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)
        )
        return corners.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
    }

    private fun isBackgroundPixel(pixel: Int, bgColor: Int, tolerance: Int): Boolean {
        return abs(Color.red(pixel) - Color.red(bgColor)) <= tolerance &&
               abs(Color.green(pixel) - Color.green(bgColor)) <= tolerance &&
               abs(Color.blue(pixel) - Color.blue(bgColor)) <= tolerance
    }

    // scanFromTop, scanFromBottom, scanFromLeft, scanFromRight は
    // それぞれ端から1ラインずつ調べ、非背景ピクセルの割合が
    // contentThreshold を超えたラインを返す
}
Copy
3. 処理パイプライン統合（PageProcessor.kt）
分割とトリミングを統合するパイプライン:

Copyclass PageProcessor @Inject constructor(
    private val splitter: PageSplitter,
    private val trimmer: MarginTrimmer
) {
    // 処理順序:
    // 1. 余白トリミング（元画像全体に対して）
    // 2. 見開き判定（トリミング後のアスペクト比で判定）
    // 3. 必要なら分割
    // これにより余白を除いた実際のコンテンツ比率で
    // 正確に見開き判定ができる

    suspend fun process(
        inputStream: InputStream,
        settings: ComicSettings
    ): List<ProcessedPage> = withContext(Dispatchers.Default) {
        // ... 実装
    }
}
4. 設定項目（settings画面に追加）
自動分割設定
自動分割: ON/OFF（デフォルト: ON）
見開き判定閾値: スライダー (1.0〜2.0、デフォルト1.3)
スマート分割線検出: ON/OFF（デフォルト: ON）
分割位置の手動微調整: スライダー (-5%〜+5%)
余白トリミング設定
自動トリミング: ON/OFF（デフォルト: OFF）
背景色許容差: スライダー (10〜60、デフォルト30)
安全マージン: スライダー (0〜20px、デフォルト2)
コンテンツ検出感度: 低/中/高（contentThresholdに対応）

---

## 処理の流れ（図解）

元画像 (例: 1800×1200 の見開きスキャン) │ ▼ [余白トリミング] ← 縮小版でスキャン、座標を元スケールに変換 │ 四辺の白帯を検出・除去 ▼ トリミング済み (例: 1750×1150) │ ▼ [見開き判定] ← width/height = 1.52 > 1.3 → 見開きと判定 │ ▼ [スマート分割線検出] ← 中央付近で最も白い縦ラインを探索 │ ▼ [BitmapRegionDecoder で半分ずつデコード] ← メモリ節約 │ ├── 右半分 (ページ1) ← 日本式の場合 └── 左半分 (ページ2)


余白トリミングは「やってみたら思ったより効くが、まれに誤検出する」タイプの機能なので、**デフォルトOFF**にして、ユーザーが試して気に入ったらONにする設計にしています。自動分割のほうはかなり安定して動作するので、デフォルトONで問題ありません。