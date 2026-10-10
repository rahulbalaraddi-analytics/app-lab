package com.applab.ideadrop.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * The "Record idea" app icon / shortcut. Has no screen of its own: it opens IdeaDrop
 * and asks it to start recording straight away. Point the side key or a lock-screen
 * shortcut at "Record idea" for one-press capture.
 */
class QuickRecordActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_QUICK_RECORD, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
        )
        finish()
    }
}
