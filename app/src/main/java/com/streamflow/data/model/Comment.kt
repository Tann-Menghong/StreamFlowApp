package com.streamflow.data.model

import org.schabi.newpipe.extractor.Page

data class Comment(
    val author: String,
    val text: String,
    val likeCount: Long,
    val avatarUrl: String,
    val isOwnerComment: Boolean,
    val publishedTime: String,
    val replyCount: Int = 0,
    val repliesPage: Page? = null // non-null when replies can be fetched
)
