package com.syrmos.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = InMemoryStubDriver()
}

private class InMemoryStubDriver : SqlDriver {
    override fun addListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
    override fun removeListener(vararg queryKeys: String, listener: app.cash.sqldelight.Query.Listener) {}
    override fun notifyListeners(vararg queryKeys: String) {}
    override fun currentTransaction(): app.cash.sqldelight.Transacter.Transaction? = null
    override fun newTransaction(): QueryResult<app.cash.sqldelight.Transacter.Transaction> =
        QueryResult.Value(object : app.cash.sqldelight.Transacter.Transaction() {
            override val enclosingTransaction: app.cash.sqldelight.Transacter.Transaction? = null
            override fun endTransaction(successful: Boolean): QueryResult<Unit> = QueryResult.Value(Unit)
        })

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.Value(0)

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = mapper(EmptyCursor)

    override fun close() {}

    private object EmptyCursor : SqlCursor {
        override fun getBoolean(index: Int): Boolean? = null
        override fun getBytes(index: Int): ByteArray? = null
        override fun getDouble(index: Int): Double? = null
        override fun getLong(index: Int): Long? = null
        override fun getString(index: Int): String? = null
        override fun next(): QueryResult.Value<Boolean> = QueryResult.Value(false)
    }
}
