package org.openrndr.dnky.post

import org.openrndr.resourceUrl
import java.net.URL

private class FilterTools

fun filterFragmentCode(resourceId: String): String {
    val urlString = resourceUrl("gl3/$resourceId", FilterTools::class.java)
    return URL(urlString).readText()
}

