package com.astrbot.android.core.common.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeletionGuardTest {

    @Test
    fun only_allows_deletion_when_another_profile_remains() {
        assertFalse(ProfileDeletionGuard.canDelete(remainingCount = 1))
        assertTrue(ProfileDeletionGuard.canDelete(remainingCount = 2))
    }

    @Test
    fun reports_blocked_catalog_kind() {
        val error = assertThrows(LastProfileDeletionBlockedException::class.java) {
            ProfileDeletionGuard.requireCanDelete(
                remainingCount = 1,
                kind = ProfileCatalogKind.PERSONA,
            )
        }

        assertEquals(ProfileCatalogKind.PERSONA, error.kind)
    }
}
