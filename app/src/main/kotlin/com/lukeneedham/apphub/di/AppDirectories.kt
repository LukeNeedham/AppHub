package com.lukeneedham.apphub.di

import android.content.Context
import java.io.File

object AppDirectories {
    /** Where downloaded APKs are kept until they are installed. */
    fun apks(context: Context): File = File(context.cacheDir, "apks")
}
