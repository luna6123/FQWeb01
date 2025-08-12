package me.fycz.fqweb.web

import android.graphics.Bitmap
import de.robv.android.xposed.XposedHelpers
import fi.iki.elonen.NanoHTTPD
import me.fycz.fqweb.MainHook.Companion.moduleRes
import me.fycz.fqweb.utils.JsonUtils
import me.fycz.fqweb.web.controller.DragonController
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val origin = session.headers["origin"] ?: "*"

        // 处理预检请求
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "")
                .apply { addCorsHeaders(origin) }
        }

        return try {
            // 规范化 content-type（可能为 null）
            session.headers["content-type"]?.let {
                val ct = ContentType(it).tryUTF8()
                session.headers["content-type"] = ct.contentTypeHeader
            }

            val route = normalizeApiRoute(session.uri)
            val parameters = session.parameters // Map<String, List<String>>

            val returnData = when (session.method) {
                Method.GET -> dispatchGet(route, parameters)
                // 如需 POST，可在此扩展并解析 body
                else -> null
            }

            if (returnData == null) {
                // 未匹配到路由，返回 404 页
                newChunkedResponse(
                    Response.Status.NOT_FOUND,
                    MIME_HTML,
                    XposedHelpers.assetAsByteArray(moduleRes, "404.html").inputStream()
                ).apply { addCorsHeaders(origin) }
            } else {
                // 二进制图片（Bitmap）直接以 PNG 流输出；否则 JSON
                if (returnData.data is Bitmap) {
                    val bmp = returnData.data as Bitmap
                    val out = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    val bytes = out.toByteArray()
                    out.close()
                    newFixedLengthResponse(
                        Response.Status.OK,
                        returnData.mime ?: "image/png",
                        ByteArrayInputStream(bytes),
                        bytes.size.toLong()
                    ).apply { addCorsHeaders(origin) }
                } else {
                    val json = JsonUtils.toJson(returnData)
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json; charset=utf-8",
                        json
                    ).apply { addCorsHeaders(origin) }
                }
            }
        } catch (e: Exception) {
            // 避免直接泄漏堆栈到前端
            val error = ReturnData().fail(500, "服务器内部错误")
            val json = JsonUtils.toJson(error)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", json)
                .apply { addCorsHeaders(session.headers["origin"] ?: "*") }
        }
    }

    private fun dispatchGet(route: String, parameters: Map<String, List<String>>): ReturnData? {
        return when (route) {
            "search" -> DragonController.search(parameters)
            "info" -> DragonController.info(parameters)
            "catalog" -> DragonController.catalog(parameters)
            "content" -> DragonController.content(parameters)
            "chapterImage" -> DragonController.chapterImage(parameters)
            "bookmall" -> DragonController.bookMall(parameters)
            "newCategory" -> DragonController.newCategory(parameters)
            else -> null
        }
    }

    private fun normalizeApiRoute(uri: String): String {
        return uri.trim()
            .removePrefix("/api")
            .removePrefix("/")
            .substringBefore("?")
    }

    private fun Response.addCorsHeaders(origin: String) {
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        addHeader("Access-Control-Allow-Origin", origin)
        addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
        addHeader("Access-Control-Max-Age", "86400")
        addHeader("Connection", "close")
    }
}
