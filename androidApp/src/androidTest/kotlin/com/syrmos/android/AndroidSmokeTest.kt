package com.syrmos.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Minimal remote UI gate; the full behavior contracts live in shared tests. */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AndroidSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun application_launches_without_crashing() {
        composeRule.waitForIdle()
    }
}
