package com.syrmos.core.common

import com.syrmos.core.common.result.SyrmosResult
import com.syrmos.core.common.result.getOrNull
import com.syrmos.core.common.result.map
import com.syrmos.core.common.result.onError
import com.syrmos.core.common.result.onSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyrmosResultTest {

    @Test
    fun success_getOrNull_returns_value() {
        val result: SyrmosResult<String> = SyrmosResult.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun error_getOrNull_returns_null() {
        val result: SyrmosResult<String> = SyrmosResult.Error("oops")
        assertNull(result.getOrNull())
    }

    @Test
    fun loading_getOrNull_returns_null() {
        val result: SyrmosResult<String> = SyrmosResult.Loading
        assertNull(result.getOrNull())
    }

    @Test
    fun map_transforms_success() {
        val result = SyrmosResult.Success(42).map { it * 2 }
        assertEquals(84, (result as SyrmosResult.Success).data)
    }

    @Test
    fun map_passes_through_error() {
        val result = SyrmosResult.Error("oops").map { 42 }
        assertTrue(result is SyrmosResult.Error)
    }

    @Test
    fun onSuccess_invoked_for_success() {
        var captured = ""
        SyrmosResult.Success("test").onSuccess { captured = it }
        assertEquals("test", captured)
    }

    @Test
    fun onSuccess_not_invoked_for_error() {
        var invoked = false
        SyrmosResult.Error("oops").onSuccess { invoked = true }
        assertTrue(!invoked)
    }

    @Test
    fun onError_invoked_for_error() {
        var captured = ""
        SyrmosResult.Error("oops").onError { msg, _ -> captured = msg }
        assertEquals("oops", captured)
    }
}
