package com.elymbot.android.plugins.tasks

import com.elymbot.core.contract.ToolInstallationIdentity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Lazy, versioned storage in the plugin-ID-scoped directory supplied by the host.
 * Constructing this object performs no I/O, so candidate plugin preparation cannot
 * migrate or alter data belonging to the currently active installation.
 */
internal class FileScheduledTaskRepository(
    private val dataDirectory: File,
) : ScheduledTaskRepository {
    private val dataFile = File(dataDirectory, DATA_FILE_NAME).toPath().toAbsolutePath().normalize()
    private val lockFile = File(dataDirectory, LOCK_FILE_NAME).toPath().toAbsolutePath().normalize()
    private val processLock = processLocks.computeIfAbsent(dataFile.toString()) { ReentrantLock() }

    override fun snapshot(): TaskStoreState = withExclusiveLock {
        readState()
    }

    override fun <T> transact(block: (TaskStoreState) -> Pair<TaskStoreState, T>): T = withExclusiveLock {
        val state = readState()
        val (nextState, result) = block(state)
        if (nextState != state) writeState(nextState)
        result
    }

    private inline fun <T> withExclusiveLock(block: () -> T): T = processLock.withLock {
        Files.createDirectories(dataFile.parent)
        FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun readState(): TaskStoreState {
        if (!Files.exists(dataFile)) return TaskStoreState()
        val size = Files.size(dataFile)
        if (size !in HEADER_SIZE.toLong()..MAX_FILE_SIZE_BYTES) {
            throw IOException("Scheduled task store has an invalid size.")
        }

        DataInputStream(BufferedInputStream(FileInputStream(dataFile.toFile()))).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Scheduled task store header is invalid.")
            val version = input.readInt()
            if (version != SCHEMA_VERSION) {
                throw IOException("Scheduled task store schema is not supported.")
            }
            val tasks = readItems(input, MAX_TASKS) { readTask(input) }.associateBy(TaskRecord::id)
            val runs = readItems(input, MAX_RUNS) { readRun(input) }.associateBy(TaskRunRecord::runId)
            val receipts = readItems(input, MAX_RECEIPTS) { readMutationReceipt(input) }
                .associateBy(MutationReceipt::requestId)
            if (input.read() != -1) throw IOException("Scheduled task store contains trailing data.")
            return TaskStoreState(tasks, runs, receipts)
        }
    }

    private fun writeState(state: TaskStoreState) {
        if (state.tasks.size > MAX_TASKS || state.runs.size > MAX_RUNS ||
            state.mutationReceipts.size > MAX_RECEIPTS
        ) {
            throw IOException("Scheduled task store entry limit was reached.")
        }
        Files.createDirectories(dataFile.parent)
        val tempPath = dataFile.resolveSibling("${dataFile.fileName}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(tempPath.toFile()).use { fileOutput ->
                DataOutputStream(BufferedOutputStream(fileOutput)).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(SCHEMA_VERSION)
                    writeItems(output, state.tasks.values.sortedBy(TaskRecord::id), ::writeTask)
                    writeItems(output, state.runs.values.sortedBy(TaskRunRecord::runId), ::writeRun)
                    writeItems(
                        output,
                        state.mutationReceipts.values.sortedBy(MutationReceipt::requestId),
                        ::writeMutationReceipt,
                    )
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            if (Files.size(tempPath) > MAX_FILE_SIZE_BYTES) {
                throw IOException("Scheduled task store size limit was reached.")
            }
            try {
                Files.move(
                    tempPath,
                    dataFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (failure: AtomicMoveNotSupportedException) {
                throw IOException("Scheduled task store does not support atomic replacement.", failure)
            }
        } finally {
            Files.deleteIfExists(tempPath)
        }
    }

    private fun readTask(input: DataInputStream): TaskRecord = TaskRecord(
        id = input.readText(),
        revision = input.readLong(),
        title = input.readText(),
        prompt = input.readText(),
        startLocalDateTime = LocalDateTime.parse(input.readText()),
        timeZoneId = input.readText(),
        repeatRule = TaskRepeatRule(
            kind = readEnum(input),
            intervalHours = input.readNullableInt(),
        ),
        endCondition = TaskEndCondition(
            kind = readEnum(input),
            endDate = input.readNullableText()?.let(LocalDate::parse),
            occurrenceCount = input.readNullableInt(),
        ),
        enabled = input.readBoolean(),
        eligibleFrom = input.readInstant(),
        creationSource = readEnum(input),
        createdAt = input.readInstant(),
        updatedAt = input.readInstant(),
    )

    private fun writeTask(output: DataOutputStream, task: TaskRecord) {
        output.writeText(task.id)
        output.writeLong(task.revision)
        output.writeText(task.title)
        output.writeText(task.prompt)
        output.writeText(task.startLocalDateTime.toString())
        output.writeText(task.timeZoneId)
        output.writeEnum(task.repeatRule.kind)
        output.writeNullableInt(task.repeatRule.intervalHours)
        output.writeEnum(task.endCondition.kind)
        output.writeNullableText(task.endCondition.endDate?.toString())
        output.writeNullableInt(task.endCondition.occurrenceCount)
        output.writeBoolean(task.enabled)
        output.writeInstant(task.eligibleFrom)
        output.writeEnum(task.creationSource)
        output.writeInstant(task.createdAt)
        output.writeInstant(task.updatedAt)
    }

    private fun readRun(input: DataInputStream): TaskRunRecord = TaskRunRecord(
        taskId = input.readText(),
        runId = input.readText(),
        occurrenceId = input.readNullableText(),
        scheduleRevision = input.readLong(),
        plannedAt = input.readNullableInstant(),
        triggeredAt = input.readInstant(),
        origin = readEnum(input),
        installationIdentity = ToolInstallationIdentity(input.readText(), input.readText()),
        requestId = input.readNullableText(),
        status = readEnum(input),
        updatedAt = input.readInstant(),
        failureKind = input.readNullableText()?.let { value ->
            try {
                TaskRunFailureKind.valueOf(value)
            } catch (failure: IllegalArgumentException) {
                throw IOException("Scheduled task run failure value is invalid.", failure)
            }
        },
    )

    private fun writeRun(output: DataOutputStream, run: TaskRunRecord) {
        output.writeText(run.taskId)
        output.writeText(run.runId)
        output.writeNullableText(run.occurrenceId)
        output.writeLong(run.scheduleRevision)
        output.writeNullableInstant(run.plannedAt)
        output.writeInstant(run.triggeredAt)
        output.writeEnum(run.origin)
        output.writeText(run.installationIdentity.pluginId)
        output.writeText(run.installationIdentity.instanceId)
        output.writeNullableText(run.requestId)
        output.writeEnum(run.status)
        output.writeInstant(run.updatedAt)
        output.writeNullableText(run.failureKind?.name)
    }

    private fun readMutationReceipt(input: DataInputStream): MutationReceipt = MutationReceipt(
        requestId = input.readText(),
        fingerprint = input.readText(),
        resultKind = readEnum(input),
        taskId = input.readText(),
        revision = input.readNullableLong(),
        runId = input.readNullableText(),
    )

    private fun writeMutationReceipt(output: DataOutputStream, receipt: MutationReceipt) {
        output.writeText(receipt.requestId)
        output.writeText(receipt.fingerprint)
        output.writeEnum(receipt.resultKind)
        output.writeText(receipt.taskId)
        output.writeNullableLong(receipt.revision)
        output.writeNullableText(receipt.runId)
    }

    private fun <T> readItems(input: DataInputStream, maximum: Int, read: () -> T): List<T> {
        val count = input.readInt()
        if (count !in 0..maximum) throw IOException("Scheduled task store entry count is invalid.")
        return List(count) { read() }
    }

    private fun <T> writeItems(output: DataOutputStream, values: List<T>, write: (DataOutputStream, T) -> Unit) {
        output.writeInt(values.size)
        values.forEach { value -> write(output, value) }
    }

    private fun DataInputStream.readText(): String = readUTF()

    private fun DataOutputStream.writeText(value: String) = writeUTF(value)

    private fun DataInputStream.readNullableText(): String? = if (readBoolean()) readText() else null

    private fun DataOutputStream.writeNullableText(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeText(value)
    }

    private fun DataInputStream.readNullableInt(): Int? = if (readBoolean()) readInt() else null

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readInstant(): Instant = Instant.ofEpochSecond(readLong(), readInt().toLong())

    private fun DataOutputStream.writeInstant(value: Instant) {
        writeLong(value.epochSecond)
        writeInt(value.nano)
    }

    private fun DataInputStream.readNullableInstant(): Instant? = if (readBoolean()) readInstant() else null

    private fun DataOutputStream.writeNullableInstant(value: Instant?) {
        writeBoolean(value != null)
        if (value != null) writeInstant(value)
    }

    private inline fun <reified T : Enum<T>> readEnum(input: DataInputStream): T = try {
        enumValueOf(input.readText())
    } catch (failure: IllegalArgumentException) {
        throw IOException("Scheduled task store enum value is invalid.", failure)
    }

    private fun DataOutputStream.writeEnum(value: Enum<*>) = writeText(value.name)

    private companion object {
        const val DATA_FILE_NAME = "scheduled-tasks.bin"
        const val LOCK_FILE_NAME = "scheduled-tasks.lock"
        const val MAGIC = 0x45595354
        const val SCHEMA_VERSION = 1
        const val HEADER_SIZE = 8
        const val MAX_FILE_SIZE_BYTES = 32L * 1024L * 1024L
        const val MAX_TASKS = 10_000
        const val MAX_RUNS = 500_000
        const val MAX_RECEIPTS = 100_000

        val processLocks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
