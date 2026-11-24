package com.a17.assist.ffvn.inject

interface Injector {
    val isReady: Boolean
    fun down(x: Int, y: Int, eventTime: Long)
    fun move(x: Int, y: Int, eventTime: Long)
    fun up(x: Int, y: Int, eventTime: Long)
}
