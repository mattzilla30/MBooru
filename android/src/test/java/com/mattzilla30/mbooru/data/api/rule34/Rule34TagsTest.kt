// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.model.common.Booru
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class Rule34TagsTest {
    @Test
    fun authenticatedTagUrlsUseFixedHostAndZeroBasedPages() {
        val action =
            ActionTag(
                Booru(
                    name = "Fixture",
                    host = "untrusted.invalid",
                    type = 7,
                    user = Rule34Credentials("42", "fixture+key").asUser(),
                ),
                query = "land*",
                order = "count",
                limit = 20,
            )
        val url = Rule34Tags.requestUrl(action, 0)
        assertEquals("api.rule34.xxx", url.host)
        assertEquals("tag", url.queryParameter("s"))
        assertEquals("0", url.queryParameter("pid"))
        assertEquals("land*", url.queryParameter("name_pattern"))
        assertEquals("fixture+key", url.queryParameter("api_key"))
        assertEquals("42", url.queryParameter("user_id"))
        assertThrows(IllegalArgumentException::class.java) { Rule34Tags.requestUrl(action, -1) }
    }

    @Test
    fun jsonAndAttributeXmlTagsHaveEquivalentMetadata() {
        val json = Rule34Tags.parse("""[{"id":17,"name":"landscape","count":42,"type":0}]""")
        val xml =
            Rule34Tags.parse(
                """<?xml version="1.0"?><tags type="array"><tag id="17" name="landscape" count="42" type="0" ambiguous="false"/></tags>"""
            )
        assertEquals(json, xml)
        assertEquals(7, json.single().booruType)
        assertEquals(emptyList<Any>(), Rule34Tags.parse("<tags/>"))
        assertEquals(
            json,
            Rule34Tags.parse(
                """{"tag":[{"id":"17","name":"landscape","count":"42","type":"0"}]}"""
            ),
        )
    }

    @Test
    fun errorsAndChallengesAreNotEmptySuccessAndSecretsAreNotEchoed() {
        listOf(
                null,
                "",
                "null",
                "{}",
                "\"fixture-secret\"",
                "<html>challenge</html>",
                "<tags><tag",
                """[{"id":1,"name":{},"count":1,"type":0}]""",
                """[{"id":1,"name":"landscape","count":-1,"type":0}]""",
                """<!DOCTYPE tags [<!ENTITY data SYSTEM "file:///etc/passwd">]><tags/>""",
            )
            .forEach { body ->
                val error = assertThrows(Rule34Exception::class.java) { Rule34Tags.parse(body) }
                assertFalse(error.message.orEmpty().contains("fixture-secret"))
            }
    }
}
