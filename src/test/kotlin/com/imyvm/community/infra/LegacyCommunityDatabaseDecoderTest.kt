package com.imyvm.community.infra

import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.MemberRoleType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LegacyCommunityDatabaseDecoderTest {
    @Test
    fun roleOnlyReleaseWithMultipleMembersHasOneExactLayout() {
        val bytes = bytes { output ->
            output.writeInt(1)
            writeRegion(output, 7)
            output.writeLong(123L)
            output.writeInt(2)
            writeRoleMember(output, FIRST_UUID, MemberRoleType.OWNER)
            writeRoleMember(output, SECOND_UUID, MemberRoleType.MEMBER)
            output.writeInt(0)
            output.writeInt(4)
        }

        val decoded = LegacyCommunityDatabaseDecoder.decode(bytes)

        assertEquals(1, decoded.communities.size)
        assertEquals(MemberRoleType.OWNER, decoded.communities.single().member[FIRST_UUID]?.basicRoleType)
        assertEquals(MemberRoleType.MEMBER, decoded.communities.single().member[SECOND_UUID]?.basicRoleType)
        assertTrue(decoded.pendingOperations.isEmpty())
    }

    @Test
    fun rawAccountReleasePreservesMultipleCommunitiesMembersMailAndTurnoverOrder() {
        val bytes = bytes { output ->
            output.writeInt(2)
            writeRawCommunity(output, 10, listOf(FIRST_UUID, SECOND_UUID))
            writeRawCommunity(output, 11, listOf(THIRD_UUID))
            output.writeInt(0)
        }

        val decoded = LegacyCommunityDatabaseDecoder.decode(bytes)

        assertEquals(listOf(10, 11), decoded.communities.map { it.regionNumberId })
        val first = decoded.communities.first().member.getValue(FIRST_UUID)
        assertEquals(listOf("mail-$FIRST_UUID"), first.mail.map { it.string })
        assertEquals(listOf(100L, 101L), first.turnover.map { it.amount })
        assertEquals(listOf(1_000L, 1_001L), first.turnover.map { it.timestamp })
    }

    @Test
    fun rawAccountReleaseWithCooldownTailIsDistinguishedByExactEnd() {
        val bytes = bytes { output ->
            output.writeInt(1)
            writeRawCommunity(output, 12, listOf(FIRST_UUID))
            output.writeInt(0)
            output.writeInt(1)
            output.writeInt(12)
            output.writeInt(1)
            output.writeUTF("global")
            output.writeLong(500L)
        }

        val decoded = LegacyCommunityDatabaseDecoder.decode(bytes)

        assertEquals(500L, decoded.communities.single().nameChangeCooldowns["global"])
    }

    @Test
    fun versionedReleasePreservesStructuredTurnoverLikesAndIncome() {
        val bytes = bytes { output ->
            output.writeInt(1)
            writeVersionedCommunity(output, 13, FIRST_UUID)
            output.writeInt(0)
            output.writeInt(0)
            output.writeInt(1)
            output.writeInt(13)
            output.writeInt(3)
            output.writeInt(1)
            output.writeUTF(SECOND_UUID.toString())
            output.writeLong(700L)
            output.writeInt(1)
            output.writeInt(13)
            writeVersionedTurnovers(output, listOf(900L))
        }

        val decoded = LegacyCommunityDatabaseDecoder.decode(bytes)
        val community = decoded.communities.single()
        val turnover = community.member.getValue(FIRST_UUID).turnover.single()

        assertEquals(TurnoverSource.PLAYER, turnover.source)
        assertEquals("legacy.member", turnover.descriptionKey)
        assertEquals(listOf("A", "B"), turnover.descriptionArgs)
        assertEquals(3, community.likeCount)
        assertEquals(700L, community.lastLikedBy[SECOND_UUID])
        assertEquals(900L, community.communityIncome.single().amount)
    }

    @Test
    fun truncatedOrTrailingGarbageInputMatchesNoPublishedLayout() {
        val valid = bytes { output ->
            output.writeInt(1)
            writeRawCommunity(output, 14, listOf(FIRST_UUID))
            output.writeInt(0)
        }
        assertFailsWith<IllegalArgumentException> {
            LegacyCommunityDatabaseDecoder.decode(valid.copyOf(valid.size - 1))
        }
        assertFailsWith<IllegalArgumentException> {
            LegacyCommunityDatabaseDecoder.decode(valid + byteArrayOf(1))
        }
    }

    private fun writeRoleMember(output: DataOutputStream, uuid: UUID, role: MemberRoleType) {
        output.writeUTF(uuid.toString())
        output.writeInt(role.value)
    }

    private fun writeRawCommunity(output: DataOutputStream, regionId: Int, members: List<UUID>) {
        writeRegion(output, regionId)
        output.writeInt(members.size)
        members.forEachIndexed { index, uuid ->
            output.writeUTF(uuid.toString())
            output.writeLong(100L + index)
            output.writeInt(if (index == 0) MemberRoleType.OWNER.value else MemberRoleType.MEMBER.value)
            output.writeInt(1)
            output.writeUTF("mail-$uuid")
            output.writeInt(2)
            output.writeLong(100L)
            output.writeLong(1_000L)
            output.writeLong(101L)
            output.writeLong(1_001L)
            output.writeBoolean(false)
            output.writeBoolean(true)
            output.writeBoolean(false)
        }
        output.writeInt(0)
        output.writeInt(4)
        output.writeInt(0)
        output.writeInt(0)
        output.writeInt(0)
        output.writeLong(250L)
    }

    private fun writeVersionedCommunity(output: DataOutputStream, regionId: Int, uuid: UUID) {
        writeRegion(output, regionId)
        output.writeInt(1)
        output.writeUTF(uuid.toString())
        output.writeLong(100L)
        output.writeInt(MemberRoleType.OWNER.value)
        output.writeInt(1)
        output.writeUTF("legacy-mail")
        writeVersionedTurnovers(output, listOf(500L), "legacy.member", listOf("A", "B"))
        output.writeBoolean(false)
        output.writeBoolean(true)
        output.writeBoolean(false)
        output.writeInt(0)
        output.writeInt(4)
        output.writeInt(0)
        writeVersionedTurnovers(output, emptyList())
        output.writeInt(0)
        output.writeLong(250L)
    }

    private fun writeVersionedTurnovers(
        output: DataOutputStream,
        amounts: List<Long>,
        descriptionKey: String = "legacy.income",
        args: List<String> = emptyList()
    ) {
        output.writeInt(-1)
        output.writeInt(amounts.size)
        amounts.forEachIndexed { index, amount ->
            output.writeLong(amount)
            output.writeLong(2_000L + index)
            output.writeInt(TurnoverSource.PLAYER.value)
            output.writeUTF(descriptionKey)
            output.writeInt(args.size)
            args.forEach(output::writeUTF)
        }
    }

    private fun writeRegion(output: DataOutputStream, regionId: Int) {
        output.writeBoolean(true)
        output.writeInt(regionId)
    }

    private fun bytes(writer: (DataOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use(writer) }.toByteArray()

    companion object {
        private val FIRST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val SECOND_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        private val THIRD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    }
}
