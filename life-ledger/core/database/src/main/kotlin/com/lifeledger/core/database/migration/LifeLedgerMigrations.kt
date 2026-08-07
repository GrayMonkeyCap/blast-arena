package com.lifeledger.core.database.migration

import androidx.room.migration.Migration

/**
 * Every schema migration, in the order Room should consider them.
 *
 * ## The policy
 *
 * **A version bump always ships an explicit [Migration].** Life Ledger's data cannot be
 * re-downloaded. The SMS inbox it was built from may have been pruned by the OS, by the user,
 * or by our own retention job; corrections the user made by hand exist nowhere else. Losing
 * the database is losing the product, so `fallbackToDestructiveMigration()` — in any of its
 * forms, including `...OnDowngrade()` — must never appear in this module. If you find
 * yourself reaching for it, the answer is a longer migration, not a shorter one.
 *
 * **`schemas/` is committed.** The exported JSON for every version lives in
 * `core/database/schemas` and is reviewed like any other source. It is what makes a schema
 * change visible in a pull request instead of implied by a diff to an entity, and it is what
 * `MigrationTestHelper` opens to create a real "old" database to migrate.
 *
 * **Each version bump adds a `MigrationTestHelper` test.** The test creates the database at
 * `n - 1` with representative rows, runs the migration with `validateMigration = true`, and
 * asserts the rows survived with the values expected. A migration that has only been run
 * forwards on a developer's empty debug build has not been tested; the rows are the point.
 *
 * **Prefer additive changes.** New nullable columns and new tables migrate in one statement
 * and cannot lose anything. When a column must change type or meaning, the migration creates
 * the new table, copies with an explicit `SELECT`, drops the old one and recreates its
 * indices — SQLite's `ALTER TABLE` cannot do it, and Room will reject a schema that only
 * looks right.
 *
 * **Do not forget the FTS index.** `transactions_fts` is an external-content table whose
 * triggers Room generates. A migration that rebuilds `transactions` must rebuild the FTS
 * table and its triggers too, then repopulate it — otherwise search silently returns nothing
 * for every row written before the upgrade, which no test that only checks column values will
 * catch.
 *
 * ## The array
 *
 * Empty at version 1: there is nothing to migrate from. It is still wired into
 * `Room.databaseBuilder(...).addMigrations(*ALL)` so that adding the first migration is a
 * one-line change in this file and nowhere else.
 */
object LifeLedgerMigrations {

    val ALL: Array<Migration> = arrayOf()
}
