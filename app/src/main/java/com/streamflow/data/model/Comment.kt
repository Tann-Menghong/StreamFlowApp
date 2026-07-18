package com.streamflow.data.model

import org.schabi.newpipe.extractor.Page

data class Comment(
    val author: String,
    val text: String,
    val likeCount: Long,
    val avatarUrl: String,
    val isOwnerComment: Boolean,
    val isPinned: Boolean = false,   // pinned by the creator (shown first by YouTube)
    val isHearted: Boolean = false,  // "hearted" by the video's creator
    val publishedTime: String,
    val replyCount: Int = 0,
    val repliesPage: Page? = null // non-null when replies can be fetched
)
