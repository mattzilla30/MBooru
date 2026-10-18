package com.mattzilla30.mbooru.ui.compose.booru

import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import org.junit.Assert.*
import org.junit.Test

class BooruValidationTest {
    @Test fun `site validation retains name host and legacy hash salt requirements`() {
        val valid = mapOf("name" to "Example", "host" to "example.org", "hashSalt" to "--${Values.HASH_SALT_CONTAINED}--")
        assertNull(validateBooru(valid, Values.BOORU_TYPE_MOE))
        assertEquals(R.string.booru_config_name_cant_empty, validateBooru(valid + ("name" to ""), Values.BOORU_TYPE_MOE))
        assertEquals(R.string.booru_config_host_invalid, validateBooru(valid + ("host" to "https://example.org"), Values.BOORU_TYPE_MOE))
        assertEquals(R.string.booru_config_hash_salt_must_contain_yp, validateBooru(valid + ("hashSalt" to "salt"), Values.BOORU_TYPE_DAN1))
        assertNull(validateBooru(valid, Values.BOORU_TYPE_DAN1))
    }
}
