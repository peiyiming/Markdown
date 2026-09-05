package com.nzf.markdown.app

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.support.multidex.MultiDex
import com.nzf.markdown.share.RenderedMarkdownShareController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Created by niezhuofu on 17-11-8.
 */
class MDApplication : Application() {
    companion object {
        var mContext: Context? = null

        fun getContext(): Context? = mContext

        fun getResources() :Resources = mContext!!.resources
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        mContext = this
        PDFBoxResourceLoader.init(applicationContext)
        registerActivityLifecycleCallbacks(RenderedMarkdownShareController(this))
    }
}