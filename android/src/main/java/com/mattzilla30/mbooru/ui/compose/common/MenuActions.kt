package com.mattzilla30.mbooru.ui.compose.common

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import org.xmlpull.v1.XmlPullParser

data class MenuAction(val item: MenuItem, val icon: Int, val direct: Boolean)

fun menuActions(context: Context, resource: Int): List<MenuAction> {
    if (resource == 0) return emptyList()
    val menu = PopupMenu(context, android.view.View(context)).apply { menuInflater.inflate(resource, menu) }.menu
    val icons = mutableMapOf<Int, Pair<Int, Boolean>>()
    context.resources.getXml(resource).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item" && parser.depth == 2) {
                val android = "http://schemas.android.com/apk/res/android"
                val app = "http://schemas.android.com/apk/res-auto"
                val id = parser.getAttributeResourceValue(android, "id", 0)
                icons[id] = parser.getAttributeResourceValue(android, "icon", 0) to
                    (parser.getAttributeIntValue(app, "showAsAction", 0) != 0)
            }
        }
    }
    return (0 until menu.size()).map { menu.getItem(it) }.map { item ->
        val (icon, direct) = icons[item.itemId] ?: (0 to false)
        MenuAction(item, icon, direct && icon != 0)
    }
}
