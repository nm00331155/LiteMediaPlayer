# プロジェクト: LiteMedia Player

## 技術スタック
- 言語: Kotlin
- 最小SDK: API 26 (Android 8.0)
- ビルド: Gradle (Kotlin DSL)
- 動画再生: Media3 (ExoPlayer)
- 画像表示: Coil
- UI: Jetpack Compose + Material3
- DI: Hilt
- ローカルDB: Room
- メモリ管理: カスタム LifecycleObserver

## モジュール構成
```text
app/
  core/      # 共通ユーティリティ, メモリ管理
  player/    # 動画プレイヤー機能
  comic/     # コミックビューア機能
  lock/      # ロック画面機能
  settings/  # 設定画面
  data/      # Room DB, DataStore
```

## 設計原則
- シンプル・軽量を最優先
- 各機能モジュールは疎結合
- メモリ使用量を常時監視・制御

## 実装フェーズ
1. プロジェクト基盤
2. 動画プレイヤー
3. コミックビューア
4. ロック画面機能
5. メモリ管理
6. 設定画面

## 追加要件（コミック）
- 見開き画像の自動分割（1ページ表示時）
- 余白の自動トリミング（設定でON/OFF）
- 分割とトリミングの処理パイプライン統合
