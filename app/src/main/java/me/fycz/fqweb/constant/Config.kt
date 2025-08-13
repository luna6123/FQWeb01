package me.fycz.fqweb.constant

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

object Config {

    // ===== 调试模式开关 =====
    private const val DEBUG_MODE = false
    private fun log(tag: String, msg: String) {
        if (DEBUG_MODE) Log.d(tag, msg)
    }

    // ===== 固定常量 =====
    const val TRAVERSAL_CONFIG_URL =
        "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val dragonClassloader by lazy {
        GlobalApp.getClassloader() ?: error("ClassLoader 获取失败")
    }

    private val prefs by lazy {
        GlobalApp.application?.getSharedPreferences("config_cache", Context.MODE_PRIVATE)
            ?: error("SharedPreferences 获取失败")
    }

    private val cache = ConcurrentHashMap<String, String>()
    private val versionKey = "last_version_code"

    // ===== 安全获取版本号（兼容 API 28+） =====
    val versionCode: Int by lazy {
        runCatching {
            val info = GlobalApp.application?.packageManager
                ?.getPackageInfo(GlobalApp.application!!.packageName, 0)
                ?: error("PackageInfo 获取失败")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.onFailure { log("ConfigError", "获取版本号失败: ${it.message}") }
         .getOrDefault(0)
    }

    init {
        val lastVersion = prefs.getInt(versionKey, -1)
        if (lastVersion != versionCode) {
            log("ConfigCache", "版本变化 $lastVersion -> $versionCode，清空缓存")
            clearCacheInternal()
            prefs.edit().putInt(versionKey, versionCode).apply()
        }
        // 异步预热缓存，减少首次访问卡顿
        CoroutineScope(Dispatchers.Default).launch {
            preheat()
        }
    }

    // ===== 缓存工具 =====
    private fun getCachedOrFind(key: String, finder: () -> String?): String {
        cache[key]?.let {
            log("ConfigCache", "命中内存缓存: $key -> $it")
            return it
        }
        prefs.getString(key, null)?.let {
            cache[key] = it
            log("ConfigCache", "命中磁盘缓存: $key -> $it")
            return it
        }
        val found = finder()
        if (found != null) {
            cache[key] = found
            prefs.edit().putString(key, found).apply()
            log("ConfigCache", "保存缓存: $key -> $found")
            return found
        }
        throw ClassNotFoundException("未找到 $key 对应类（版本: $versionCode）")
    }

    // ===== 精准清空缓存（保留版本号） =====
    fun clearCache() {
        clearCacheInternal()
        prefs.edit().putInt(versionKey, versionCode).apply()
        log("ConfigCache", "手动清空缓存完成")
    }

    private fun clearCacheInternal() {
        prefs.edit()
            .remove("settingRecyclerAdapter")
            .remove("settingItemQSN")
            .remove("settingItemStrFieldName")
            .remove("rpcApiPackage")
            .remove("readerFullRequestClz")
            .apply()
        cache.clear()
    }

    // ===== 高性能扫描工具（包前缀 + 多关键字过滤 + 释放资源） =====
    private fun scanClasses(
        packagePrefix: String,
        keywords: List<String> = emptyList(),
        match: (Class<*>) -> Boolean
    ): String? {
        return runCatching {
            val dexPathListField = dragonClassloader.javaClass
                .getDeclaredField("pathList").apply { isAccessible = true }
            val dexPathList = dexPathListField.get(dragonClassloader)

            val dexElementsField = dexPathList.javaClass
                .getDeclaredField("dexElements").apply { isAccessible = true }
            val dexElements = dexElementsField.get(dexPathList) as Array<*>

            for (element in dexElements) {
                val dexFileField = element?.javaClass
                    ?.getDeclaredField("dexFile")?.apply { isAccessible = true }
                val dexFile = dexFileField?.get(element) as? DexFile ?: continue
                dexFile.useSafe { df ->
                    val entries = df.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement()
                        if (name.startsWith(packagePrefix) &&
                            (keywords.isEmpty() || keywords.all { name.contains(it, true) })
                        ) {
                            runCatching {
                                val clazz = name.findClass(dragonClassloader)
                                if (match(clazz)) {
                                    log("ConfigScan", "Found match: $name")
                                    return name
                                }
                            }.onFailure {
                                log("ConfigError", "加载类失败: $name -> ${it.message}")
                            }
                        }
                    }
                }
            }
            null
        }.onFailure {
            log("ConfigError", "扫描失败: ${it.message}")
        }.getOrNull()
    }

    // 安全 close DexFile
    private inline fun <T> DexFile.useSafe(block: (DexFile) -> T): T {
        return try {
            block(this)
        } finally {
            runCatching { 
                if (Build.VERSION.SDK_INT >= 26) 
                    (this as? Closeable)?.close() 
            }
        }
    }

    // ===== 动态探测类（多候选类名回退） =====
    val settingRecyclerAdapterClz: String
        get() = getCachedOrFind("settingRecyclerAdapter") {
            scanClasses("com.dragon.read", listOf("recyler")) {
                RecyclerView.Adapter::class.java.isAssignableFrom(it)
            } ?: tryFallback(
                "com.dragon.read.recyler.c",
                "com.dragon.read.base.recyler.c"
            )
        }

    val settingItemQSNClz: String
        get() = getCachedOrFind("settingItemQSN") {
            scanClasses("com.dragon.read.component.biz.impl.mine.settings") { cls ->
                cls.declaredMethods.any { it.name.length == 1 }
            } ?: tryFallback(
                "com.dragon.read.component.biz.impl.mine.settings.a.k",
                "com.dragon.read.component.biz.impl.mine.settings.a.g"
            )
        }

    val settingItemStrFieldName: String
        get() = prefs.getString("settingItemStrFieldName", null) ?: run {
            val settingItemClz = scanClasses("com.dragon.read.pages.mine.settings") { true }
                ?.findClass(dragonClassloader)
            val fieldName = settingItemClz?.declaredFields
                ?.firstOrNull { f -> f.type == CharSequence::class.java }
                ?.name ?: "i"
            prefs.edit().putString("settingItemStrFieldName", fieldName).apply()
            cache["settingItemStrFieldName"] = fieldName
            fieldName
        }

    val rpcApiPackage: String
        get() = getCachedOrFind("rpcApiPackage") {
            scanClasses("com.dragon.read.rpc") { cls ->
                cls.simpleName in listOf("e", "f")
            }?.substringBeforeLast(".") ?: "com.dragon.read.rpc"
        }

    val readerFullRequestClz: String
        get() = getCachedOrFind("readerFullRequestClz") {
            val fullReqClz = "$rpcModelPackage.FullRequest".findClass(dragonClassloader)
            val eClz = "$rpcApiPackage.e"
            if (runCatching {
                    XposedHelpers.findMethodExact(eClz, dragonClassloader, "a", fullReqClz)
                }.isSuccess) eClz else "$rpcApiPackage.f"
        }

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    // 多候选尝试
    private fun tryFallback(vararg candidates: String): String? {
        for (name in candidates) {
            if (runCatching { name.findClass(dragonClassloader) }.isSuccess) {
                log("ConfigFallback", "使用回退类: $name")
                return name
            }
        }
        return null
    }

    // 异步预热关键类，减少首次访问卡顿
    private fun preheat() {
        log("ConfigPreheat", "开始
