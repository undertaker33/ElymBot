package com.astrbot.android.ui.settings

import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceCenterCreationTest {

    @Test
    fun `remote mcp draft builds a remote server resource item`() {
        val resource = RemoteMcpServerDraft(
            name = "Docs MCP",
            description = "Remote docs server",
            serverUrl = "https://mcp.example.com/http",
            timeoutSeconds = "45",
            active = true,
        ).toResourceItem()
        val payload = JSONObject(resource.payloadJson)

        assertEquals(ResourceCenterKind.MCP_SERVER, resource.kind)
        assertEquals("Docs MCP", resource.name)
        assertEquals("Remote docs server", resource.description)
        assertEquals("", resource.content)
        assertEquals("local", resource.source)
        assertEquals("https://mcp.example.com/http", payload.getString("url"))
        assertEquals("streamable_http", payload.getString("transport"))
        assertEquals(45, payload.getInt("timeoutSeconds"))
        assertFalse(payload.has("stdio"))
        assertFalse(payload.has("command"))
    }

    @Test
    fun `tool skill draft stores template in payload instead of plain content`() {
        val resource = SkillResourceDraft(
            name = "Summarizer",
            description = "Shorten long answers",
            content = "Summarize the latest response in three bullet points.",
            skillKind = SkillResourceKind.TOOL,
            active = false,
        ).toResourceItem()
        val payload = JSONObject(resource.payloadJson)

        assertEquals(ResourceCenterKind.SKILL, resource.kind)
        assertEquals(SkillResourceKind.TOOL, resource.skillKind)
        assertEquals("Summarizer", resource.name)
        assertEquals("Shorten long answers", resource.description)
        assertEquals("", resource.content)
        assertEquals("tool", payload.getString("skill_kind"))
        assertEquals(
            "Summarize the latest response in three bullet points.",
            payload.getString("resultTemplate"),
        )
        assertTrue(payload.getJSONObject("inputSchema").has("type"))
        assertFalse(resource.enabled)
    }

    @Test
    fun `prompt skill draft keeps prompt text in content field`() {
        val resource = SkillResourceDraft(
            name = "Concise Reply",
            description = "Keep replies short",
            content = "Reply in at most three short sentences.",
            skillKind = SkillResourceKind.PROMPT,
            active = true,
        ).toResourceItem()
        val payload = JSONObject(resource.payloadJson)

        assertEquals(ResourceCenterKind.SKILL, resource.kind)
        assertEquals(SkillResourceKind.PROMPT, resource.skillKind)
        assertEquals("Reply in at most three short sentences.", resource.content)
        assertEquals("prompt", payload.getString("skill_kind"))
        assertFalse(payload.has("resultTemplate"))
        assertTrue(resource.enabled)
    }
}
