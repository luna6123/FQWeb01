package me.fycz.fqweb.constant

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass
import java.util.concurrent.ConcurrentHashMap

object Config {

    // ===== 调试模式开关 =====
    private const val DEBUG_MODE = true
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
        }.getOrElse {
            log("ConfigError", "获取版本号失败: ${it.message}")
            0
        }
    }

    init {
        val lastVersion = prefs.getInt(versionKey, -1)
        if (lastVersion != versionCode) {
            log("ConfigCache", "版本变化 $lastVersion -> $versionCode，清空缓存")
            clearCacheInternal()
            prefs.edit().putInt(versionKey, versionCode).apply()
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

    // ===== 清空缓存接口 =====
    fun clearCache() {
        clearCacheInternal()
        prefs.edit().putInt(versionKey, versionCode).apply()
        log("ConfigCache", "手动清空缓存完成")
    }

    // 精准清空（保留版本号）
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

    // ===== 高性能扫描工具（包前缀 + 关键字过滤） =====
    private fun scanClasses(
        packagePrefix: String,
        keyword: String? = null,
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
                val entries = dexFile.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith(packagePrefix) &&
                        (keyword == null || name.contains(keyword, true))) {
                        runCatching {
                            val clazz = name.findClass(dragonClassloader)
                            if (match(clazz)) {
                                log("ConfigScan", "Found: $name")
                                return name
                            }
                        }
                    }
                }
            }
            null
        }.getOrElse {
            log("ConfigError", "扫描失败: ${it.message}")
            null
        }
    }

    // ===== 动态探测类（多回退策略） =====
    val settingRecyclerAdapterClz: String by lazy {
        getCachedOrFind("settingRecyclerAdapter") {
            scanClasses("com.dragon.read", "recyler") { 
                RecyclerView.Adapter::class.java.isAssignableFrom(it) 
            } ?: tryFallback(
                "com.dragon.read.recyler.c",
                "com.dragon.read.base.recyler.c"
            )
        }
    }

    val settingItemQSNClz: String by lazy {
        getCachedOrFind("settingItemQSN") {
            scanClasses("com.dragon.read.component.biz.impl.mine.settings") { cls ->
                cls.declaredMethods.any { it.name.length == 1 }
            } ?: tryFallback(
                "com.dragon.read.component.biz.impl.mine.settings.a.k",
                "com.dragon.read.component.biz.impl.mine.settings.a.g"
            )
        }
    }

    val settingItemStrFieldName: String by lazy {
        prefs.getString("settingItemStrFieldName", null) ?: run {
            val settingItemClz = scanClasses("com.dragon.read.pages.mine.settings") { true }
                ?.findClass(dragonClassloader)
            val fieldName = settingItemClz?.declaredFields
                ?.firstOrNull { f -> f.type == CharSequence::class.java }
                ?.name ?: "i"
            prefs.edit().putString("settingItemStrFieldName", fieldName).apply()
            cache["settingItemStrFieldName"] = fieldName
            fieldName
        }
    }

    val rpcApiPackage: String by lazy {
        getCachedOrFind("rpcApiPackage") {
            scanClasses("com.dragon.read.rpc") { 
                it.simpleName in listOf("e", "f") 
            }?.substringBeforeLast(".") ?: "com.dragon.read.rpc"
        }
    }

    val readerFullRequestClz: String by lazy {
        getCachedOrFind("readerFullRequestClz") {
            val fullReqClz = "$rpcModelPackage.FullRequest".findClass(dragonClassloader)
            val eClz = "$rpcApiPackage.e"
            if (runCatching {
                    XposedHelpers.findMethodExact(eClz, dragonClassloader, "a", fullReqClz)
                }.isSuccess) eClz else "$rpcApiPackage.f"
        }
    }

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    // ===== 多候选类名尝试 =====
    private fun tryFallback(vararg candidates: String): String? {
        for (name in candidates) {
            if (runCatching { name.findClass(dragonClassloader) }.isSuccess) return name
        }
        return null
    }
}
