package com.syrmos.core.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

const val DATABASE_NAME = "syrmos.db"
