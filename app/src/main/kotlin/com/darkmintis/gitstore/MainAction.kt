package com.darkmintis.gitstore

sealed interface MainAction {
    data object DismissRateLimitDialog : MainAction
    data object AppUpdateLater : MainAction
    data object AppUpdateIgnore : MainAction
    data object AppUpdateNow : MainAction
}
