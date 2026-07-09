package com.streamflow.data.model

data class Comment(
    val author: String,
    val text: String,
    val likeCount: Long,
    val avatarUrl: String,
    val isOwnerComment: Boolean,
    val publishedTime: String,
    val replyCount: Int = 0
)
