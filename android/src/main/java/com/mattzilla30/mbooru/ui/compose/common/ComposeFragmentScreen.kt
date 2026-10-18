package com.mattzilla30.mbooru.ui.compose.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import com.mattzilla30.mbooru.ui.base.BaseActivity

/** Shared Compose app bar for secondary screens whose fragment content is still being migrated. */
@OptIn(ExperimentalMaterial3Api::class)
fun <T : Fragment> BaseActivity.setComposeFragmentScreen(@StringRes title: Int, fragmentClass: Class<T>) {
    setScreenTitle(title)
    setComposeContent { AndroidFragment(clazz = fragmentClass, modifier = Modifier.fillMaxSize()) }
}
