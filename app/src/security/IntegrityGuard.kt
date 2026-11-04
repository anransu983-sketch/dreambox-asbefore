package com.suanran.dreambox.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * 签名自校验（加固）：运行时核对自身签名证书 SHA-256，
 * 被二次打包/换签后拒绝运行。期望值拆段拼合，避免明文整串落在 dex 里。
 * 仅在非 DEBUG 构建启用，本地调试包不受影响。
 */
object IntegrityGuard {

    private val expected: String by lazy {
        val a = "6d36efd38983ad01"
        val b = "1a68bfd951340908"
        val c = "495d6aedf67fb36a"
        val d = "f1d178c47956e34c"
        a + b + c + d
    }

    fun verify(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
        if (certs.isEmpty()) return@runCatching false
        val md = MessageDigest.getInstance("SHA-256")
        certs.any { cert ->
            val digest = md.digest(cert)
            val hex = digest.joinToString("") { "%02x".format(it) }
            constantTimeEquals(hex, expected)
        }
    }.getOrDefault(false)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
