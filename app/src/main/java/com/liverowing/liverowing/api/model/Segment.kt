package com.liverowing.liverowing.api.model

import com.parse.ParseClassName
import com.parse.ParseFile
import com.parse.ParseObject

/**
 * Created by henrikmalmberg on 2017-10-01.
 */
@ParseClassName("Segment")
class Segment : ParseObject() {
    var value by ParseDelegate<Int?>()
    var name by ParseDelegate<String?>()
    var restType by ParseDelegate<Int?>()
    var restValue by ParseDelegate<Int?>()
    var valueType by ParseDelegate<Int?>()
    var restDescription by ParseDelegate<String?>()
    var author by ParseDelegate<User?>()
    var image by ParseDelegate<ParseFile?>()
    var targetRate by ParseDelegate<Int?>()

}
