package com.example.litemediaplayer.comic

enum class TouchAction(val label: String) {
    NEXT_PAGE("次のページ"),
    PREV_PAGE("前のページ"),
    TOGGLE_CONTROLS("コントロール表示切替"),
    FIRST_PAGE("最初のページ"),
    LAST_PAGE("最後のページ"),
    JUMP_TO_PAGE("指定ページに移動"),
    SKIP_FORWARD("指定ページ数進む"),
    SKIP_BACKWARD("指定ページ数戻る"),
    TOGGLE_FULLSCREEN("最大化切替"),
    NONE("なし")
}
