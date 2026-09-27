package com.molvess.para_defteri_flutter

import android.app.Activity
import android.content.Intent
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private var pending: MethodChannel.Result? = null
    private var exportText: String? = null
    private val io = Executors.newSingleThreadExecutor()

    override fun onResume() {
        super.onResume()
        // A preference, not an FPS promise: Android and battery policy retain control.
        @Suppress("DEPRECATION")
        val screen = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        screen?.let { d ->
            val current = d.mode
            val fastest = d.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }.maxByOrNull { it.refreshRate }
            if (fastest != null) {
                window.attributes = window.attributes.apply { preferredRefreshRate = fastest.refreshRate }
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.molvess.ledger/files")
            .setMethodCallHandler { call, result ->
                if (pending != null) {
                    result.error("BUSY", "Başka bir dosya işlemi sürüyor.", null)
                    return@setMethodCallHandler
                }
                try {
                    when (call.method) {
                        "display" -> {
                            @Suppress("DEPRECATION")
                            val screen = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
                            result.success(mapOf(
                                "activeHz" to screen?.refreshRate?.toDouble(),
                                "preferredHz" to window.attributes.preferredRefreshRate.toDouble(),
                                "supportedHz" to screen?.supportedModes?.map { it.refreshRate.toDouble() }?.distinct()?.sorted()
                            ))
                        }
                        "save" -> {
                            exportText = call.argument<String>("text") ?: error("Eksik dosya içeriği")
                            pending = result
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = call.argument<String>("mime") ?: "application/json"
                                putExtra(Intent.EXTRA_TITLE, call.argument<String>("name"))
                            }
                            @Suppress("DEPRECATION")
                            startActivityForResult(intent, 401)
                        }
                        "open" -> {
                            pending = result
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/csv", "text/plain", "application/octet-stream", "application/vnd.ms-excel"))
                            }
                            @Suppress("DEPRECATION")
                            startActivityForResult(intent, 402)
                        }
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    pending = null
                    exportText = null
                    result.error("FILES", "Dosya seçici açılamadı.", null)
                }
            }
    }

    @Deprecated("Platform activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 401 && requestCode != 402) return
        val callback = pending ?: return
        val content = exportText
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            pending = null
            exportText = null
            callback.success(null)
            return
        }
        io.execute {
            try {
                val value: Any = if (requestCode == 401) {
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write((content ?: error("İçerik kayboldu")).toByteArray(Charsets.UTF_8))
                    } ?: error("Dosya yazılamadı")
                    true
                } else {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val buffer = CharArray(8192)
                        val text = StringBuilder()
                        var count = reader.read(buffer)
                        while (count >= 0) {
                            text.append(buffer, 0, count)
                            if (text.length > 20_000_000) error("Dosya çok büyük")
                            count = reader.read(buffer)
                        }
                        text.toString()
                    } ?: error("Dosya okunamadı")
                }
                runOnUiThread { pending = null; exportText = null; callback.success(value) }
            } catch (e: Exception) {
                runOnUiThread { pending = null; exportText = null; callback.error("FILES", "Dosya okunamadı/yazılamadı.", null) }
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
