package me.fycz.fqweb.web.controller

import android.graphics.Bitmap
import android.util.Base64
import me.fycz.fqweb.utils.getObjectField
import me.fycz.fqweb.web.ReturnData
import me.fycz.fqweb.web.service.DragonService
import java.io.ByteArrayOutputStream

object DragonController {

    private fun Map<String, List<String>>.param(name: String): String? =
        this[name]?.firstOrNull()?.takeIf { it.isNotEmpty() }

    fun search(parameters: Map<String, List<String>>): ReturnData {
        val keyword = parameters.param("query") ?: return ReturnData().setErrorMsg("参数query不能为空")
        val page = parameters.param("page")?.toIntOrNull() ?: 1
        return runCatching {
            ReturnData().setData(DragonService.search(keyword, page))
        }.getOrElse { ReturnData().setErrorMsg("搜索失败: ${it.message ?: "未知错误"}") }
    }

    fun info(parameters: Map<String, List<String>>): ReturnData {
        val bookId = parameters.param("book_id") ?: return ReturnData().setErrorMsg("参数book_id不能为空")
        return runCatching {
            ReturnData().setData(DragonService.getInfo(bookId))
        }.getOrElse { ReturnData().setErrorMsg("获取书籍详情失败: ${it.message ?: "未知错误"}") }
    }

    fun catalog(parameters: Map<String, List<String>>): ReturnData {
        val bookId = parameters.param("book_id") ?: return ReturnData().setErrorMsg("参数book_id不能为空")
        return runCatching {
            ReturnData().setData(DragonService.getCatalog(bookId))
        }.getOrElse { ReturnData().setErrorMsg("获取目录失败: ${it.message ?: "未知错误"}") }
    }

    fun content(parameters: Map<String, List<String>>): ReturnData {
        val itemId = parameters.param("item_id") ?: return ReturnData().setErrorMsg("参数item_id不能为空")
        return runCatching {
            val content = DragonService.getContent(itemId)
            runCatching {
                // 尝试预解码内容（不强制）
                val data = content.getObjectField("data") as Any
                DragonService.decodeContent(data)
            }
            ReturnData().setData(content)
        }.getOrElse { ReturnData().setErrorMsg("获取内容失败: ${it.message ?: "未知错误"}") }
    }

    // 支持两种返回：
    // 1) 默认 JSON：返回 data_uri base64
    // 2) format=binary：返回 Bitmap，HttpServer 以 image/png 流式输出
    fun chapterImage(parameters: Map<String, List<String>>): ReturnData {
        val itemId = parameters.param("item_id") ?: return ReturnData().setErrorMsg("参数 item_id 不能为空")
        val format = parameters.param("format") ?: "json"

        return runCatching {
            val bmp: Bitmap = DragonService.generateChapterImage(itemId)
            if (format.equals("binary", ignoreCase = true)) {
                ReturnData().ok(bmp, mime = "image/png")
            } else {
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                baos.close()
                val result = mapOf(
                    "item_id" to itemId,
                    "mime" to "image/png",
                    "width" to bmp.width,
                    "height" to bmp.height,
                    "data_uri" to "data:image/png;base64,$base64"
                )
                ReturnData().ok(result, mime = "application/json")
            }
        }.getOrElse { ReturnData().setErrorMsg("生成章节图片失败: ${it.message ?: "未知错误"}") }
    }

    fun bookMall(parameters: Map<String, List<String>>): ReturnData {
        return runCatching {
            ReturnData().setData(DragonService.bookMall(parameters))
        }.getOrElse { ReturnData().setErrorMsg("获取书城数据失败: ${it.message ?: "未知错误"}") }
    }

    fun newCategory(parameters: Map<String, List<String>>): ReturnData {
        return runCatching {
            ReturnData().setData(DragonService.newCategory(parameters))
        }.getOrElse { ReturnData().setErrorMsg("获取新分类失败: ${it.message ?: "未知错误"}") }
    }
}
