package com.example.ar_lab2

import com.google.ar.core.Anchor

data class ARObject(
    val id: Long,
    val anchor: Anchor,
    val rotation: Float = 0f,
    val scale: Float = 1f
)