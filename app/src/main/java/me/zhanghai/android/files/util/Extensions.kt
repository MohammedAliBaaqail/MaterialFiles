package me.zhanghai.android.files.util

import java8.nio.file.Path
import java.security.MessageDigest

val Path.pathString: String get() = toString()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) } 