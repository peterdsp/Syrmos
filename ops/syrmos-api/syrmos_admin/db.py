"""SQLite connection + migrations runner."""
from __future__ import annotations

import os
import sqlite3
from pathlib import Path

DEFAULT_DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(Path(__file__).resolve().parent.parent / "data" / "syrmos.db"),
)
MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "migrations"


def connect(db_path: str = DEFAULT_DB_PATH) -> sqlite3.Connection:
    Path(db_path).parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA journal_mode = WAL")
    return conn


def current_version(conn: sqlite3.Connection) -> int:
    row = conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'"
    ).fetchone()
    if not row:
        return 0
    row = conn.execute("SELECT MAX(version) AS v FROM schema_version").fetchone()
    return int(row["v"] or 0)


def migrate(conn: sqlite3.Connection) -> int:
    applied = current_version(conn)
    files = sorted(MIGRATIONS_DIR.glob("[0-9]*.sql"))
    for path in files:
        n = int(path.stem.split("_", 1)[0])
        if n <= applied:
            continue
        sql = path.read_text(encoding="utf-8")
        conn.executescript(sql)
    return current_version(conn)


if __name__ == "__main__":
    with connect() as c:
        v = migrate(c)
        print(f"DB ready at {DEFAULT_DB_PATH}, version={v}")
