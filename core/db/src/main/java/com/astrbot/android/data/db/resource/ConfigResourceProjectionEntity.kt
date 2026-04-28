package com.astrbot.android.data.db.resource

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.astrbot.android.data.db.ConfigProfileEntity

@Entity(
    tableName = "config_resource_projections",
    primaryKeys = ["configId", "kind", "resourceId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ResourceCenterItemEntity::class,
            parentColumns = ["resourceId"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["configId", "kind", "sortIndex"]),
        Index(value = ["resourceId"]),
    ],
)
data class ConfigResourceProjectionEntity(
    val configId: String,
    val resourceId: String,
    val kind: String,
    val active: Boolean,
    val priority: Int,
    val sortIndex: Int,
    val configJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)
