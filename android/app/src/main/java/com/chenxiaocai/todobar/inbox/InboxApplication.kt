package com.chenxiaocai.todobar.inbox

import android.app.Application

class InboxApplication : Application() {
    val database by lazy { InboxDatabase(this) }
    val secureStore by lazy { SecureStore(this) }
}
