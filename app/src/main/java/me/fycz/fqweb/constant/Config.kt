package me.fycz.fqweb.constant

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass

object Config {

    // ===== 固定常量 =====
    const val TRAVERSAL_CONFIG_URL =
        "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val dragonClassloader by lazy { GlobalApp.getClassloader() }
    private val prefs by lazy {
        GlobalApp.application!!.getSharedPreferences("config_cache", Context.MODE_PRIVATE)
    }

    private val versionKey = "last_version_code"

    val versionCode: Int by lazy {
        runCatching {
            GlobalApp.application?.packageManager
                ?.getPackageInfo(GlobalApp.application!!.packageName, 0)?.versionCode
        }.getOrElse { 0 }
    }

    init {
        // 应用启动时检查版本是否变化
        val lastVersion = prefs.getInt(versionKey, -1)
        if (lastVersion != versionCode) {
            Log.d("ConfigCache", "检测到版本变化: $lastVersion -> $versionCode，清空缓存")
            clearCacheInternal()
            prefs.edit().putInt(versionKey, versionCode).apply()
        }
    }

    // ===== 缓存工具 =====
    private fun getCachedOrFind(key: String, finder: () -> String?): String {
        prefs.getString(key, null)?.let {
            Log.d("ConfigCache", "命中缓存: $key -> $it")
            return it
        }
        val found = finder()
        if (found != null) {
            prefs.edit().putString(key, found).apply()
            Log.d("ConfigCache", "保存缓存: $key -> $found")
            return found
        }
        throw ClassNotFoundException("未找到 $key 对应类")
    }

    // ===== 清空缓存接口（外部可调用） =====
    fun clearCache() {
        clearCacheInternal()
        prefs.edit().putInt(versionKey, versionCode).apply()
        Log.d("ConfigCache", "手动清空缓存完成")
    }

    // 内部清空方法
    private fun clearCacheInternal() {
        prefs.edit().clear().apply()
    }

    // ===== 扫描工具 =====
    private fun scanClasses(packagePrefix: String, match: (Class<*>) -> Boolean): String? {
        return runCatching {
            val dexPathListField = dragonClassloader.javaClass
                .getDeclaredField("pathList").apply { isAccessible = true }
            val dexPathList = dexPathListField.get(dragonClassloader)

            val dexElementsField = dexPathList.javaClass
                .getDeclaredField("dexElements").apply { isAccessible = true }
            val dexElements = dexElementsField.get(dexPathList) as Array<*>

            for (element in dexElements) {
                val dexFileField = element?.javaClass?.getDeclaredField("dexFile")?.apply {
                    isAccessible = true
                }
                val dexFile = dexFileField?.get(element) as? DexFile ?: continue
                val entries = dexFile.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (name.startsWith(packagePrefix)) {
                        runCatching {
                            val clazz = name.findClass(dragonClassloader)
                            if (match(clazz)) {
                                Log.d("ConfigScan", "Found match: $name")
                                return name
                            }
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    // ===== 动态探测类 =====
    val settingRecyclerAdapterClz: String by lazy {
        getCachedOrFind("settingRecyclerAdapter") {
            scanClasses("com.dragon.read") { cls ->
                RecyclerView.Adapter::class.java.isAssignableFrom(cls) &&
                        cls.name.contains("recyler", true)
            } ?: "com.dragon.read.recyler.c"
        }
    }

    val settingItemQSNClz: String by lazy {
        getCachedOrFind("settingItemQSN") {
            scanClasses("com.dragon.read.component.biz.impl.mine.settings") { cls ->
                cls.declaredMethods.any { it.name.length == 1 }
            } ?: "com.dragon.read.component.biz.impl.mine.settings.a.k"
        }
    }

    val settingItemStrFieldName: String by lazy {
        prefs.getString("settingItemStrFieldName", null)
            ?: run {
                val settingItemClz = scanClasses("com.dragon.read.pages.mine.settings") { true }
                    ?.findClass(dragonClassloader)
                val fieldName = settingItemClz?.declaredFields?.firstOrNull { f ->
                    f.type == CharSequence::class.java
                }?.name ?: "i"
                prefs.edit().putString("settingItemStrFieldName", fieldName).apply()
                fieldName
            }
    }

    val rpcApiPackage: String by lazy {
        getCachedOrFind("rpcApiPackage") {
            scanClasses("com.dragon.read.rpc") { cls ->
                cls.simpleName in listOf("e", "f")
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
}
