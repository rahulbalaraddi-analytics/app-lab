package com.applab.ideadrop.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/** Audio file shared/opened into IdeaDrop from another app, waiting for a language choice. */
object SharedAudio {
    val pending = MutableStateFlow<Uri?>(null)
}
