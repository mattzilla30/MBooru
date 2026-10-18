package com.mattzilla30.mbooru.core.model

import org.junit.Assert.*
import org.junit.Test

class ResumePolicyTest {
    @Test fun `weak validators never permit byte range continuation`() {
        assertNull(ResumePolicy.validator("W/\"old\"", null))
        assertEquals("yesterday", ResumePolicy.validator("W/\"old\"", "yesterday"))
    }
    @Test fun `only a matching complete tail can be appended`() {
        assertTrue(ResumePolicy.acceptsPartial(100, "bytes 100-199/200", "\"v1\"", "\"v1\""))
        assertFalse(ResumePolicy.acceptsPartial(100, "bytes 100-199/200", "\"v1\"", "\"v2\""))
        assertFalse(ResumePolicy.acceptsPartial(100, "bytes 0-199/200", "\"v1\"", "\"v1\""))
        assertFalse(ResumePolicy.acceptsPartial(100, "bytes 100-150/200", "\"v1\"", "\"v1\""))
        assertFalse(ResumePolicy.acceptsPartial(100, "bytes 100-199/*", "\"v1\"", "\"v1\""))
        assertFalse(ResumePolicy.acceptsPartial(100, "invalid", "\"v1\"", "\"v1\""))
    }
    @Test fun `retry after values are bounded and authentication is distinct`() {
        assertEquals(NetworkFailure.Authentication, NetworkFailure.http(401))
        assertEquals(NetworkFailure.RateLimited(60), NetworkFailure.http(429, "60"))
        assertEquals(NetworkFailure.RateLimited(null), NetworkFailure.http(429, "-1"))
        assertEquals(NetworkFailure.Unavailable, NetworkFailure.http(503))
    }
}
