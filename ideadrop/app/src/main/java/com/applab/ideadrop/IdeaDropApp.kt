package com.applab.ideadrop

import android.app.Application
import com.applab.ideadrop.data.AppDatabase
import com.applab.ideadrop.data.NoteRepository
import com.applab.ideadrop.data.SettingsStore

/** Simple manual dependency container. */
class IdeaDropApp : Application() {
    lateinit var repository: NoteRepository private set
    lateinit var settings: SettingsStore private set

    override fun onCreate() {
        super.onCreate()
        repository = NoteRepository(AppDatabase.create(this).noteDao())
        settings = SettingsStore(this)
    }
}
