package org.gnit.lucenekmp.integration.r8

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R8StoredFieldsTest {
    @Test
    fun readsNumericStoredFieldsAfterR8Minification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            R8StoredFieldsApplication.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

        assertTrue(
            "The release-minified stored-fields probe did not complete",
            preferences.getBoolean(R8StoredFieldsApplication.COMPLETED_KEY, false)
        )
        assertNull(preferences.getString(R8StoredFieldsApplication.FAILURE_KEY, null))
    }
}
