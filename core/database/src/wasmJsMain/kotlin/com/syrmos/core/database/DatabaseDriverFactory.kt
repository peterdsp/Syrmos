package com.syrmos.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver {
        return WebWorkerDriver(
            Worker(
                js("""new URL("@aspect-build/aspect-workflows-action/dist/worker.sql-wasm.js", import.meta.url)""")
            )
        ).also { SyrmosDatabase.Schema.create(it) }
    }
}
