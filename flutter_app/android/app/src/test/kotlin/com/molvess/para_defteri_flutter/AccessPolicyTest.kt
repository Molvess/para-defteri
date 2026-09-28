package com.molvess.para_defteri_flutter

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTest {
    @Test fun legacyPhotoRequiresStorageRead() {
        for (sdk in listOf(24, 28, 29, 32)) {
            assertEquals(listOf(AccessPolicy.READ), AccessPolicy.permissionsFor(403, sdk))
            assertFalse(AccessPolicy.allowed(403, sdk) { false })
            assertTrue(AccessPolicy.allowed(403, sdk) { it == AccessPolicy.READ })
        }
    }

    @Test fun android13RequiresImagesNotOldStorage() {
        assertEquals(listOf(AccessPolicy.IMAGES), AccessPolicy.permissionsFor(403, 33))
        assertFalse(AccessPolicy.allowed(403, 33) { it == AccessPolicy.READ })
        assertTrue(AccessPolicy.allowed(403, 33) { it == AccessPolicy.IMAGES })
    }

    @Test fun selectedPhotoGrantIsSufficientAndRevocationBlocks() {
        for (sdk in 34..36) {
            assertEquals(listOf(AccessPolicy.IMAGES, AccessPolicy.SELECTED), AccessPolicy.permissionsFor(403, sdk))
            assertTrue(AccessPolicy.allowed(403, sdk) { it == AccessPolicy.SELECTED })
            assertTrue(AccessPolicy.allowed(403, sdk) { it == AccessPolicy.IMAGES })
            assertFalse(AccessPolicy.allowed(403, sdk) { false })
        }
    }

    @Test fun legacyDocumentReadAndWriteAreSeparate() {
        assertEquals(listOf(AccessPolicy.READ), AccessPolicy.permissionsFor(402, 28))
        assertEquals(listOf(AccessPolicy.WRITE), AccessPolicy.permissionsFor(401, 28))
        assertFalse(AccessPolicy.allowed(401, 28) { it == AccessPolicy.READ })
        assertTrue(AccessPolicy.allowed(401, 28) { it == AccessPolicy.WRITE })
        assertFalse(AccessPolicy.allowed(402, 28) { false })
    }

    @Test fun modernDocumentsNeverRequestPhotoOrAllFilesPermission() {
        for (sdk in 29..36) for (operation in listOf(401, 402)) {
            assertTrue(AccessPolicy.permissionsFor(operation, sdk).isEmpty())
            assertTrue(AccessPolicy.allowed(operation, sdk) { false })
        }
    }
}
