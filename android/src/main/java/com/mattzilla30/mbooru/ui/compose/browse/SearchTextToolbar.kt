package com.mattzilla30.mbooru.ui.compose.browse

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import com.mattzilla30.mbooru.R

/** Preserve the search selection action alongside Android's standard editing actions. */
internal class SearchTextToolbar(private val view: View, private val addSelection: () -> Unit) : TextToolbar {
    private var mode: ActionMode? = null
    private var bounds = ComposeRect.Zero
    override val status get() = if (mode == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown
    override fun hide() { mode?.finish(); mode = null }
    override fun showMenu(rect: ComposeRect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        hide()
        bounds = rect
        val actions = listOf(android.R.string.copy to onCopyRequested, android.R.string.paste to onPasteRequested,
            android.R.string.cut to onCutRequested, android.R.string.selectAll to onSelectAllRequested,
            R.string.action_add_to_muzei to addSelection).filter { it.second != null }
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                actions.forEachIndexed { index, action -> menu.add(0, index, index, action.first).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                actions[item.itemId].second?.invoke(); mode.finish(); return true
            }
            override fun onDestroyActionMode(mode: ActionMode) { this@SearchTextToolbar.mode = null }
        }
        mode = if (android.os.Build.VERSION.SDK_INT >= 23) startFloating(callback) else view.startActionMode(callback)
    }

    @androidx.annotation.RequiresApi(23)
    private fun startFloating(callback: ActionMode.Callback): ActionMode? =
        view.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = callback.onCreateActionMode(mode, menu)
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = callback.onActionItemClicked(mode, item)
            override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                outRect.set(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            }
        }, ActionMode.TYPE_FLOATING)
}
