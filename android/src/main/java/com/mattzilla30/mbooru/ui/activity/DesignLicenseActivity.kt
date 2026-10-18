package com.mattzilla30.mbooru.ui.activity

import android.os.Bundle
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent

class DesignLicenseActivity : BaseActivity() {
    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.sphere_license)
        val notice = assets.open("licenses/boorusphere.txt").bufferedReader().use { it.readText() }
        setComposeContent {
            Text(notice, Modifier.verticalScroll(rememberScrollState()).padding(24.dp))
        }
    }
}
