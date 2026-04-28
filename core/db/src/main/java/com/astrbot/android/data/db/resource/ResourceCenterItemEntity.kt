package com.astrbot.android.data.db.resource

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "resource_center_items",
    indices = [
        Index(value = ["kind", "name"]),
    ],
)
data class ResourceCenterItemEntity(
    @PrimaryKey val resourceId: String,
    val kind: String,
    val skillKind: String?,
    val name: String,
    val description: String,
    val content: String,
    val payloadJson: String,
    val source: String,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
