package com.itangcent.easyapi.core.export

import java.nio.file.Path
import kotlin.time.Duration

/**
 * An [ExternalChangeSync] for tests: records what it was asked to sync, runs [onSync] and answers
 * [outcome] at once. Tests that call the service on the EDT need it: the platform's sync waits for the EDT.
 *
 * @param onSync stands in for what a sync brings in from disk, such as a file that appears
 */
internal class FakeExternalChangeSync(
    private val outcome: SyncOutcome = SyncOutcome.Synced,
    private val onSync: () -> Unit = {}
) : ExternalChangeSync {

    /** The files of each call, in call order. */
    val requests = mutableListOf<List<Path>>()

    override suspend fun sync(files: Collection<Path>, timeout: Duration): SyncOutcome {
        requests += files.toList()
        onSync()
        return outcome
    }
}
