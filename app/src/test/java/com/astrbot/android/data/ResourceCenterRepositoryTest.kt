package com.astrbot.android.data

import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceCenterRepositoryTest {
    @Test
    fun repository_supportsInMemoryOperationsBeforeInitialize() = runTest {
        val resource = ResourceCenterItem(
            resourceId = "repo-test-mcp",
            kind = ResourceCenterKind.MCP_SERVER,
            name = "Repository Test MCP",
            description = "In-memory resource",
            payloadJson = "{\"transport\":\"sse\"}",
        )
        val projection = ConfigResourceProjection(
            configId = "config-repo-test",
            resourceId = resource.resourceId,
            kind = resource.kind,
            active = true,
            priority = 7,
            sortIndex = 2,
            configJson = "{\"timeoutSeconds\":60}",
        )

        ResourceCenterRepository.deleteResource(resource.resourceId)
        ResourceCenterRepository.saveResource(resource)
        ResourceCenterRepository.setProjection(projection)

        assertEquals(resource, ResourceCenterRepository.listResources(ResourceCenterKind.MCP_SERVER).single { it.resourceId == resource.resourceId })
        assertEquals(listOf(projection), ResourceCenterRepository.projectionsForConfig("config-repo-test"))

        ResourceCenterRepository.deleteResource(resource.resourceId)

        assertTrue(ResourceCenterRepository.listResources(ResourceCenterKind.MCP_SERVER).none { it.resourceId == resource.resourceId })
        assertTrue(ResourceCenterRepository.projectionsForConfig("config-repo-test").none { it.resourceId == resource.resourceId })
    }
}
