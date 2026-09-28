package com.molvess.para_defteri_flutter

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.MediaStore
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private var pending: MethodChannel.Result? = null
    private var exportText: String? = null
    private var permissionAction: (() -> Unit)? = null
    private var permissionOperation = 0
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
                        "pickPhoto" -> {
                            pending = result
                            withPermission(403) { openPhotoPicker() }
                        }
                        "save" -> {
                            exportText = call.argument<String>("text") ?: error("Eksik dosya içeriği")
                            pending = result
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = call.argument<String>("mime") ?: "application/json"
                                putExtra(Intent.EXTRA_TITLE, call.argument<String>("name"))
                            }
                            @Suppress("DEPRECATION")
                            withPermission(401) { startActivityForResult(intent, 401) }
                        }
                        "open" -> {
                            pending = result
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/csv", "text/plain", "application/octet-stream", "application/vnd.ms-excel"))
                            }
                            @Suppress("DEPRECATION")
                            withPermission(402) { startActivityForResult(intent, 402) }
                        }
                        "openSettings" -> {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                            result.success(null)
                        }
                        else -> result.notImplemented()
                    }
                } catch (e: SecurityException) {
                    permissionAction = null
                    pending = null
                    exportText = null
                    result.error("ACCESS_DENIED", "Seçiciye erişim reddedildi. Fotoğraf/dosya sağlayıcınızın ayarlarını kontrol edip tekrar deneyin.", null)
                } catch (e: Exception) {
                    permissionAction = null
                    pending = null
                    exportText = null
                    result.error("FILES", "Dosya seçici açılamadı.", null)
                }
            }
    }

    private fun withPermission(operation: Int, action: () -> Unit) {
        if (AccessPolicy.allowed(operation, Build.VERSION.SDK_INT) {
                checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }) {
            action()
            return
        }
        permissionOperation = operation
        permissionAction = action
        requestPermissions(AccessPolicy.permissionsFor(operation, Build.VERSION.SDK_INT).toTypedArray(), 501)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 501) return
        val action = permissionAction ?: return
        permissionAction = null
        val callback = pending ?: return
        val allowed = grantResults.isNotEmpty() && AccessPolicy.allowed(permissionOperation, Build.VERSION.SDK_INT) {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allowed) {
            val blocked = permissions.isNotEmpty() && permissions.all { !shouldShowRequestPermissionRationale(it) }
            pending = null
            exportText = null
            callback.error(if (blocked) "PERMISSION_BLOCKED" else "PERMISSION_DENIED",
                "Erişim izni verilmedi; fotoğraf/dosya seçicisi açılmadı. Kayıtlarınız değişmedi.", null)
            return
        }
        try {
            action()
        } catch (_: Exception) {
            pending = null
            exportText = null
            callback.error("FILES", "İzin alındı ancak fotoğraf/dosya seçicisi açılamadı. Tekrar deneyin.", null)
        }
    }

    @Suppress("DEPRECATION")
    private fun openPhotoPicker() {
        val photos = Intent(Intent.ACTION_GET_CONTENT).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = "image/*"
            setPackage("com.google.android.apps.photos")
        }
        if (photos.resolveActivity(packageManager) != null) {
            try {
                startActivityForResult(photos, 403)
                return
            } catch (_: ActivityNotFoundException) { /* Try the system photo picker. */ }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                startActivityForResult(Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, 403)
                return
            } catch (_: ActivityNotFoundException) { /* Older gallery fallback below. */ }
        }
        startActivityForResult(Intent(Intent.ACTION_PICK).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        }, 403)
    }

    @Deprecated("Platform activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 401 && requestCode != 402 && requestCode != 403) return
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
                val value: Any = if (requestCode == 403) {
                    readPhoto(uri)
                } else if (requestCode == 401) {
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
            } catch (e: SecurityException) {
                runOnUiThread {
                    pending = null
                    exportText = null
                    callback.error("ACCESS_DENIED", "Seçilen dosyaya erişim izni alınamadı veya süresi doldu. İşlemi yeniden başlatıp dosyayı tekrar seçin. Kayıtlarınız değiştirilmedi.", null)
                }
            } catch (e: Exception) {
                runOnUiThread { pending = null; exportText = null; callback.error("FILES", if (requestCode == 403) "Fotoğraf açılamadı. Başka bir JPEG/PNG fotoğraf seçin." else "Dosya okunamadı/yazılamadı.", null) }
            }
        }
    }

    // Permission is checked before selection. Only the subsequently chosen image
    // is processed; the app does not enumerate the broader permitted library.
    private fun readPhoto(uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Geçersiz görsel")
        val orientation = try {
            contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val side = minOf(oriented.width, oriented.height)
        val cropped = Bitmap.createBitmap(oriented, (oriented.width - side) / 2, (oriented.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(cropped, minOf(side, 256), minOf(side, 256), true)
        try {
            val output = ByteArrayOutputStream()
            check(small.compress(Bitmap.CompressFormat.JPEG, 88, output))
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            listOf(small, cropped, oriented, decoded).distinct().forEach { it.recycle() }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
