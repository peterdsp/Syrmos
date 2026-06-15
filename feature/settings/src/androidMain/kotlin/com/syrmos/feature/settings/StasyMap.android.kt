package com.syrmos.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberStasyMapOpener(): () -> Unit {
    val context = LocalContext.current
    return {
        try {
            // Copy the bundled PDF to the app's cache so a content:// URI can
            // be granted to the chosen PDF viewer (assets:// isn't shareable).
            val outFile = File(context.cacheDir, "stasy_system_map.pdf")
            if (!outFile.exists() || outFile.length() == 0L) {
                context.assets.open("stasy_system_map.pdf").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, outFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.localizedMessage ?: "Couldn't open the map",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
