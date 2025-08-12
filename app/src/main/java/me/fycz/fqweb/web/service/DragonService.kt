package me.fycz.fqweb.web.service

import android.graphics.Bitmap
import me.fycz.fqweb.constant.Config
import me.fycz.fqweb.utils.FiledNameUtils
import me.fycz.fqweb.utils.GlobalApp
import me.fycz.fqweb.utils.callMethod
import me.fycz.fqweb.utils.callStaticMethod
import me.fycz.fqweb.utils.findClass
import me.fycz.fqweb.utils.findField
import me.fycz.fqweb.utils.log
import me.fycz.fqweb.utils.new
import me.fycz.fqweb.utils.setBooleanField
import me.fycz.fqweb.utils.setFloatField
import me.fycz.fqweb.utils.setIntField
import me.fycz.fqweb.utils.setLongField
import me.fycz.fqweb.utils.setObjectField
import me.fycz.fqweb.utils.setShortField

object DragonService {

    private val dragonClassLoader: ClassLoader by lazy { GlobalApp.getClassloader() }

    fun search(keyword: String, page: Int = 1): Any {
        val GetSearchPageRequest =
            "${Config.rpcModelPackage}.GetSearchPageRequest".findClass(dragonClassLoader)
        val req = GetSearchPageRequest.newInstance()
        req.setIntField("bookshelfSearchPlan", 4)
        req.setIntField("bookstoreTab", 2)
        req.setObjectField("clickedContent", "page_search_button")
        req.setObjectField("query", keyword)
        req.setObjectField(
            "searchSource",
            "${Config.rpcModelPackage}.SearchSource"
                .findClass(dragonClassLoader)
                .callStaticMethod("findByValue", arrayOf(Int::class.java), 1)
        )
        req.setObjectField("searchSourceId", "clks###")
        req.setObjectField("tabName", "store")
        req.setObjectField(
            "tabType",
            "${Config.rpcModelPackage}.SearchTabType"
                .findClass(dragonClassLoader)
                .callStaticMethod("findByValue", arrayOf(Int::class.java), 1)
        )
        req.setShortField("userIsLogin", 1)
        setField(req, "offset", (page - 1) * 10)
        setField(req, "passback", (page - 1) * 10)
        return callFunction("${Config.rpcApiPackage}.a", obj = req, funcName = "b")
    }

    fun getInfo(bookId: String): Any {
        val BookDetailRequest =
            "${Config.rpcModelPackage}.BookDetailRequest".findClass(dragonClassLoader)
        val req = BookDetailRequest.newInstance()
        req.setLongField("bookId", bookId.toLong())
        return callFunction("${Config.rpcApiPackage}.a", obj = req)
    }

    fun getCatalog(bookId: String): Any {
        val GetDirectoryForItemIdRequest =
            "${Config.rpcModelPackage}.GetDirectoryForItemIdRequest".findClass(dragonClassLoader)
        val req = GetDirectoryForItemIdRequest.newInstance()
        req.setLongField("bookId", bookId.toLong())
        return callFunction("${Config.rpcApiPackage}.a", obj = req)
    }

    fun getContent(itemId: String): Any {
        val FullRequest = "${Config.rpcModelPackage}.FullRequest".findClass(dragonClassLoader)
        val req = FullRequest.newInstance()
        req.setObjectField("itemId", itemId)
        return callFunction(Config.readerFullRequestClz, obj = req)
    }

    fun decodeContent(itemContent: Any): Any {
        return runCatching {
            "com.dragon.read.reader.bookend.a.a"
                .findClass(dragonClassLoader)
                .new(null)
                .callMethod("a", itemContent)!!
                .callMethod("blockingFirst")!!
        }.getOrElse { throw RuntimeException("解码内容失败: ${it.message}", it) }
    }

    fun generateChapterImage(itemId: String): Bitmap {
        val bmp = Bitmap.createBitmap(512, 768, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("章节插图：$itemId", 60f, 120f, paint)
        return bmp
    }

    fun bookMall(parameters: Map<String, List<String>>): Any {
        val Clz = "${Config.rpcModelPackage}.GetBookMallCellChangeRequest".findClass(dragonClassLoader)
        val req = Clz.newInstance()
        parameters.forEach { (key, values) ->
            values.firstOrNull()?.let { setField(req, key, it) }
        }
        return callFunction("${Config.rpcApiPackage}.a", obj = req)
    }

    fun newCategory(parameters: Map<String, List<String>>): Any {
        val Clz = "${Config.rpcModelPackage}.GetNewCategoryLandingPageRequest".findClass(dragonClassLoader)
        val req = Clz.newInstance()
        parameters.forEach { (key, values) ->
            values.firstOrNull()?.let { setField(req, key, it) }
        }
        return callFunction("${Config.rpcApiPackage}.a", obj = req)
    }

    private fun callFunction(clzName: String, funcName: String = "a", obj: Any): Any {
        return try {
            clzName.findClass(dragonClassLoader)
                .callStaticMethod(funcName, obj)!!
                .callMethod("blockingFirst")!!
        } catch (e: Throwable) {
            // 保持抛出，让上层按需转换为 ReturnData 错误
            throw RuntimeException("调用RPC失败($clzName.$funcName): ${e.message}", e)
        }
    }

    private fun setField(obj: Any, name: String, value: Any) {
        try {
            val fieldName = FiledNameUtils.underlineToCamel(name)
            val field = obj.findField(fieldName)
            val type = field?.type

            val str = value.toString()
            when (type) {
                Short::class.java, java.lang.Short::class.java -> obj.setShortField(fieldName, str.toShort())
                Int::class.java, Integer::class.java -> obj.setIntField(fieldName, str.toInt())
                Long::class.java, java.lang.Long::class.java -> obj.setLongField(fieldName, str.toLong())
                Float::class.java, java.lang.Float::class.java -> obj.setFloatField(fieldName, str.toFloat())
                Boolean::class.java, java.lang.Boolean::class.java -> obj.setBooleanField(fieldName, str.toBooleanStrictOrNull() ?: (str == "1"))
                else -> {
                    if (type != null && type.isEnum) {
                        // 优先 findByValue(int)，否则 valueOf(String)
                        val enumObj = runCatching {
                            type.callStaticMethod("findByValue", str.toInt())
                        }.getOrElse {
                            java.lang.Enum.valueOf(type as Class<out Enum<*>>, str)
                        }
                        obj.setObjectField(fieldName, enumObj)
                    } else {
                        obj.setObjectField(fieldName, str)
                    }
                }
            }
        } catch (e: Throwable) {
            log("Set field $name=$value error:\n${e.stackTraceToString()}")
        }
    }

    // 安全转换
    private fun String.toBooleanStrictOrNull(): Boolean? = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
