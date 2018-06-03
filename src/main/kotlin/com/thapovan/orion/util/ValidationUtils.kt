package com.thapovan.orion.util

fun isNullOrEmpty(input: String?): Boolean {
    var isEmptyString: Boolean = false
    if(input === null){
        isEmptyString = true
    }else if(input.trim().length === 0){
        isEmptyString = true
    }
    return isEmptyString
}