package com.liverowing.android.model.parse

import com.parse.ParseClassName
import com.parse.ParseObject
import com.parse.ParseRelation

/**
 * Created by henrikmalmberg on 2017-10-01.
 */
@ParseClassName("Affiliate")
class Affiliate : ParseObject() {
    var name by ParseDelegate<String?>()
    var zipCode by ParseDelegate<String?>()
    var createdBy by ParseDelegate<User?>()
    var trainers by ParseDelegate<ParseRelation<User>?>()
    var members by ParseDelegate<ParseRelation<User>?>()
    var users by ParseDelegate<ParseRelation<User>?>()
    var description by ParseDelegate<String?>()
}