// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.security

/** Process-memory grant only. A cold process or a background transition requires device unlock. */
object AppLockSession {
    private val grant = java.util.concurrent.atomic.AtomicBoolean(false)
    val unlocked
        get() = grant.get()

    fun unlock() {
        grant.set(true)
    }

    fun lock() {
        grant.set(false)
    }
}
