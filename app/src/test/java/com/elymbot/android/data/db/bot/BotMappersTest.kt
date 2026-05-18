package com.elymbot.android.data.db.bot

import com.elymbot.android.data.db.BotAggregate
import com.elymbot.android.data.db.BotBoundQqUinEntity
import com.elymbot.android.data.db.BotEntity
import com.elymbot.android.data.db.BotTriggerWordEntity
import com.elymbot.android.data.db.toProfile
import com.elymbot.android.data.db.toWriteModel
import com.elymbot.android.model.BotProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class BotMappersTest {
    @Test
    fun aggregate_toProfile_restoresListFields() {
        val profile = BotAggregate(
            bot = BotEntity("qq-main", "QQ", "Bot", "tag", "10001", true, true, "bridge", "ws://", "provider", "persona", "config", "Idle", 1L),
            boundQqUins = listOf(BotBoundQqUinEntity("qq-main", "10001", 0)),
            triggerWords = listOf(BotTriggerWordEntity("qq-main", "elymbot", 0)),
        ).toProfile()

        assertEquals(listOf("10001"), profile.boundQqUins)
        assertEquals(listOf("elymbot"), profile.triggerWords)
    }

    @Test
    fun profile_toWriteModel_flattensListFields() {
        val writeModel = BotProfile(id = "qq-main", boundQqUins = listOf("10001"), triggerWords = listOf("elymbot")).toWriteModel()
        assertEquals(1, writeModel.boundQqUins.size)
        assertEquals(1, writeModel.triggerWords.size)
    }
}
