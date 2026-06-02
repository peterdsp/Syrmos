package com.syrmos.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver {
        return NativeSqliteDriver(
            schema = SyrmosDatabase.Schema,
            name = DATABASE_NAME,
        )
    }
}
