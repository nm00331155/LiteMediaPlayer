package com.example.litemediaplayer.comic

enum class TouchZoneLayout(val label: String) {
    THREE_COLUMN("3列 (左/中/右)"),
    SIX_ZONE("6ゾーン (上3+下3)")
}

data class TouchZoneConfig(
    val layout: TouchZoneLayout = TouchZoneLayout.THREE_COLUMN,
    val leftTap: TouchAction = TouchAction.NONE,
    val centerTap: TouchAction = TouchAction.TOGGLE_CONTROLS,
    val rightTap: TouchAction = TouchAction.NONE,
    val leftLongPress: TouchAction = TouchAction.FIRST_PAGE,
    val centerLongPress: TouchAction = TouchAction.JUMP_TO_PAGE,
    val rightLongPress: TouchAction = TouchAction.LAST_PAGE,
    val topLeftTap: TouchAction = TouchAction.PREV_PAGE,
    val topCenterTap: TouchAction = TouchAction.TOGGLE_CONTROLS,
    val topRightTap: TouchAction = TouchAction.NEXT_PAGE,
    val topLeftLongPress: TouchAction = TouchAction.FIRST_PAGE,
    val topCenterLongPress: TouchAction = TouchAction.JUMP_TO_PAGE,
    val topRightLongPress: TouchAction = TouchAction.LAST_PAGE,
    val bottomLeftTap: TouchAction = TouchAction.SKIP_BACKWARD,
    val bottomCenterTap: TouchAction = TouchAction.TOGGLE_FULLSCREEN,
    val bottomRightTap: TouchAction = TouchAction.SKIP_FORWARD,
    val bottomLeftLongPress: TouchAction = TouchAction.NONE,
    val bottomCenterLongPress: TouchAction = TouchAction.NONE,
    val bottomRightLongPress: TouchAction = TouchAction.NONE,
    val longPressMs: Int = 500,
    val skipPageCount: Int = 10,
    val volumeUpAction: TouchAction = TouchAction.NEXT_PAGE,
    val volumeDownAction: TouchAction = TouchAction.PREV_PAGE
) {
    fun resolvedForDirection(direction: ReadingDirection): TouchZoneConfig {
        if (direction == ReadingDirection.LTR) {
            return this
        }
        return this
    }
}
