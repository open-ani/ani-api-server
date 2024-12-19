package me.him188.ani.danmaku.server.util

import androidx.collection.IntList
import androidx.collection.MutableIntList

fun List<Int>.toIntList(): IntList {
    val intList = MutableIntList(size)
    this.forEach {
        intList.add(it)
    }
    intList.trim()
    return intList
}

fun Set<Int>.toIntList(): IntList {
    val intList = MutableIntList(size)
    this.forEach {
        intList.add(it)
    }
    intList.trim()
    return intList
}

fun IntList.toList(): List<Int> {
    val list = ArrayList<Int>(this.size)
    forEach {
        list.add(it)
    }
    return list
}

fun IntList.trim(): IntList {
    return MutableIntList(size).apply {
        addAll(this@trim)
    }
}
