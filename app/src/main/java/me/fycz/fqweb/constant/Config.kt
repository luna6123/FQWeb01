package me.fycz.fqweb.constant

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import dalvik.system.DexFile
import de.robv.android.xposed.XposedHelpers
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass

/**
 * 改进版 Config.kt
 * 增加自动扫描类/字段特征，减少版本更新适配成本
 */
object Config {

    const val TRAVERSAL_CONFIG_URL =
        "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val dragonClassloader by lazy { GlobalApp.getClassloader() }

    /** 动态扫描包下所有类名 */
    private fun scanPackage(pkg: String): List<String> {
        return try {
            val dex = DexFile(GlobalApp.application!!.packageCodePath)
            dex.entries().asSequence()
                .filter { it.startsWith(pkg) }
                .toList()
        } catch (e: Exception) {
            Log.e("Config", "扫描包失败: $pkg", e)
            emptyList()
        }
    }

    /** 公共类定位函数 */
    private fun findTargetClass(
        candidates: List<String>,
        validator: (Class<*>) -> Boolean
    ): String? {
        // 尝试候选
        for (name in candidates) {
            runCatching {
                val clz = name.findClass(dragonClassloader)
                if (validator(clz)) return name
            }
        }
        // 扫描包
        val pkg = candidates.first().substringBeforeLast(".")
        return scanPackage(pkg).firstOrNull { name ->
            runCatching {
                val clz = name.findClass(dragonClassloader)
                validator(clz)
            }.getOrDefault(false)
        }
    }

    /** 定位 Recycler Adapter */
    val settingRecyclerAdapterClz: String by lazy {
        findTargetClass(
            listOf(
                "com.dragon.read.base.recyler.c",
                "com.dragon.read.recyler.c"
            )
        ) { RecyclerView.Adapter::class.java.isAssignableFrom(it) }
            ?: error("找不到 settingRecyclerAdapter 类")
    }

    /** 定位 SettingItem */
    val settingItemQSNClz: String by lazy {
        val prefix = "com.dragon.read.component.biz.impl.mine.settings.a"
        findTargetClass(
            listOf("$prefix.g", "$prefix.k")
        ) { clz ->
            clz.declaredFields.any { it.type == CharSequence::class.java }
        } ?: error("找不到 settingItemQSN 类")
    }

    /** 定位 SettingItem 里保存标题的字段名 */
    val settingItemStrFieldName: String by lazy {
        val clz = settingItemQSNClz.findClass(dragonClassloader)
        listOf("i", "j").firstOrNull { name ->
            runCatching {
                val field = XposedHelpers.findField(clz, name)
                field.type == CharSequence::class.java
            }.getOrDefault(false)
        } ?: error("找不到 SettingItem 字段")
    }

    /** 定位 readerFullRequest 类 */
    val readerFullRequestClz: String by lazy {
        val fullReqClass =
            "$rpcModelPackage.FullRequest".findClass(dragonClassloader)
        findTargetClass(
            listOf("$rpcApiPackage.e", "$rpcApiPackage.f")
        ) { clz ->
            runCatching {
                XposedHelpers.findMethodExact(
                    clz.name,
                    dragonClassloader,
                    "a",
                    fullReqClass
                )
                true
            }.getOrDefault(false)
        } ?: error("找不到 readerFullRequest 类")
    }

    /** 定位 rpcApiPackage */
    val rpcApiPackage: String by lazy {
        val prefix = "com.dragon.read.rpc"
        // 先尝试已知
        listOf("$prefix.a", "$prefix.rpc").firstOrNull {
            runCatching { it.findClass(dragonClassloader) }.isSuccess
        } ?: run {
            // 扫描匹配
            scanPackage(prefix).firstOrNull { name ->
                name.endsWith(".rpc") || name.endsWith(".a")
            }?.substringBeforeLast(".")
                ?: error("找不到 rpcApiPackage")
        }
    }

    const val rpcModelPackage = "com.dragon.read.rpc.model"

    /** 获取版本号 */
    val versionCode: Int by lazy {
        try {
            val manager = GlobalApp.application!!.packageManager
            val info = manager.getPackageInfo(GlobalApp.application!!.packageName, 0)
            info.versionCode
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
