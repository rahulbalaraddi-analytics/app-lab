package com.applab.docsnap

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class DocSnapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
