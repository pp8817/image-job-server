package ai.realteeth.imagejobserver.worker.repository

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ClaimSqlProvider {

    val claimQueuedSql: String
        get() = loadSql("db/claim-queued.sql")

    val requeueStaleSql: String
        get() = loadSql("db/requeue-stale.sql")

    val extendLeaseSql: String
        get() = loadSql("db/extend-lease.sql")

    val selectStaleExhaustedSql: String
        get() = loadSql("db/select-stale-exhausted.sql")

    private val cache = ConcurrentHashMap<String, String>()

    private fun loadSql(path: String): String {
        return cache.getOrPut(path) {
            val fileResource = FileSystemResource(path)
            if (fileResource.exists()) {
                return@getOrPut fileResource.inputStream.bufferedReader().use { it.readText() }
            }

            val classPathResource = ClassPathResource(path)
            if (classPathResource.exists()) {
                return@getOrPut classPathResource.inputStream.bufferedReader().use { it.readText() }
            }

            throw IllegalStateException("Unable to find $path")
        }
    }
}
