package com.imyvm.community.infra.account

import com.imyvm.community.domain.model.account.PlayerIdentity
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

class PlayerIdentityDirectory(private val directory: Path) {
    init {
        Files.createDirectories(directory)
    }

    fun save(identity: PlayerIdentity) {
        require(identity.trustedName.isNotBlank())
        require(identity.trustedName != identity.uuid.toString())
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(1)
                output.writeLong(identity.uuid.mostSignificantBits)
                output.writeLong(identity.uuid.leastSignificantBits)
                output.writeUTF(identity.trustedName)
                output.writeLong(identity.updatedAtMillis)
            }
        }.toByteArray()
        writeAtomic(path(identity.uuid), payload)
    }

    fun find(uuid: UUID): PlayerIdentity? {
        val file = path(uuid)
        if (!Files.exists(file)) return null
        return DataInputStream(Files.newInputStream(file)).use { input ->
            require(input.readInt() == 1) { "Unsupported player identity version" }
            val storedUuid = UUID(input.readLong(), input.readLong())
            require(storedUuid == uuid) { "Player identity UUID mismatch" }
            val name = input.readUTF()
            require(name.isNotBlank() && name != uuid.toString()) { "Untrusted player identity name" }
            PlayerIdentity(storedUuid, name, input.readLong())
        }
    }

    private fun path(uuid: UUID): Path = directory.resolve("$uuid.identity")

    private fun writeAtomic(target: Path, payload: ByteArray) {
        val temporary = Files.createTempFile(directory, ".identity-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(payload)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            moveAtomic(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveAtomic(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.io.IOException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
