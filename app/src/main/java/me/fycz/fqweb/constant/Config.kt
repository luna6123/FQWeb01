package me.fycz.fqweb.constant

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.XposedHelpers
import dalvik.system.DexFile
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.findClass

object Config {

    const val TRAVERSAL_CONFIG_URL =
        "https://gitee.com/sunianOvO/FQWeb/blob/patch-1/traversal/config.json"

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val dragonClassloader by lazy { GlobalApp.getClassloader() }

    val versionCode: Int by lazy {
        try {
            val pm = GlobalApp.application!!.packageManager
            val info = pm.getPackageInfo(GlobalApp.application!!.packageName, 0)
            info.versionCode
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    // ------------------ 动态定位核心工具方法 ------------------

    /** 扫描某个包下所有类名 */
    private fun scanPackage(pkg: String): List<String> {
        return try {
            val dex = DexFile(GlobalApp.application!!.packageCodePath)
            dex.entries().toList().filter { it.startsWith(pkg) }
        } catch (e: Throwable) {
            Log.e("Config", "扫描包失败: $pkg", e)
            emptyList()
        }
    }

    /** 按候选列表 + 校验器匹配类 */
    private fun findTargetClass(candidates: List<String>, validator: (Class<*>) -> Boolean): String? {
        // 先尝试已知候选
        candidates.forEach { name ->
            runCatching {
                val clz = name.findClass(dragonClassloader)
                if (validator(clz)) return name
            }
        }
        // 再扫描包路径兜底
        val basePkg = candidates.firstOrNull()?.substringBeforeLast(".") ?: return null
        return scanPackage(basePkg).firstOrNull { name ->
            runCatching {
                val clz = name.findClass(dragonClassloader)
                validator(clz)
            }.getOrDefault(false)
        }
    }

    /** 按候选列表匹配字段名 */
    private fun findTargetField(className: String, fieldNames: List<String>, type: Class<*>): String? {
        runCatching {
            val clz = className.findClass(dragonClassloader)
            // 先尝试已知字段
            fieldNames.forEach { fName ->
                runCatching {
                    val field = XposedHelpers.findField(clz, fName)
                    if (field.type == type) return fName
                }
            }
            // 再动态查找
            clz.declaredFields.firstOrNull { it.type == type }?.let { return it.name }
        }
        return null
    }

    // ------------------ 适配属性 ------------------

    val settingRecyclerAdapterClz: String by lazy {
        findTargetClass(
            listOf(
                "com.dragon.read.base.recyler.c",
                "com.dragon.read.recyler.c"
            )
        ) { RecyclerView.Adapter::class.java.isAssignableFrom(it) }
            ?: error("找不到 settingRecyclerAdapter 类")
    }

    val settingItemQSNClz: String by lazy {
        val prefix = "com.dragon.read.component.biz.impl.mine.settings.a"
        findTargetClass(
            listOf("$prefix.g", "$prefix.k")
        ) { clz ->
            // 假设特征是含有 CharSequence 字段
            clz.declaredFields.any { f -> f.type == CharSequence::class.java }
        } ?: error("找不到 settingItemQSN 类")
    }

    val settingItemStrFieldName: String by lazy {
        findTargetField(
            "com.dragon.read.pages.mine.settings.e",
            listOf("i", "j"),
            CharSequence::class.java
        ) ?: error("找不到 settingItemStr 字段")
    }

    val readerFullRequestClz: String by lazy {
        val FullRequest = "$rpcModelPackage.FullRequest".findClass(dragonClassloader)
        findTargetClass(
            listOf("$rpcApiPackage.e", "$rpcApiPackage.f")
        ) { clz ->
            runCatching {
                XposedHelpers.findMethodExact(clz.name, dragonClassloader, "a", FullRequest)
                true
            }.getOrDefault(false)
        } ?: error("找不到 readerFullRequest 类")
    }

    val rpcApiPackage: String by lazy {
        val prefix = "com.dragon.read.rpc"
        // 校验：包下是否含有 e 或 f 类
        findTargetClass(
            listOf("$prefix.a", "$prefix.rpc")
        ) { clz ->
            clz.name.endsWith(".e") || clz.name.endsWith(".f")
        }?.substringBeforeLast(".") ?: error("找不到 rpcApiPackage")
    }

    const val rpcModelPackage = "com.dragon.read.rpc.model"
}
