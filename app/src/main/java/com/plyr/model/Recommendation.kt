package com.plyr.model

import java.util.UUID

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val inviteCode: String? = null,
    val groupType: String, // "general" or "private"
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val nickname: String,
    val joinedAt: Long = System.currentTimeMillis()
)

data class Recommendation(
    val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val nickname: String,
    val url: String,
    val comment: String? = null,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val reportCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

