/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.suanran.dreambox.feature.profiles.domain

import com.suanran.dreambox.core.model.FetchStatus
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.util.coroutine.safeRunSilent
import com.suanran.dreambox.runtime.api.contract.ProfileRepositoryContract
import com.suanran.dreambox.runtime.api.remote.IFetchObserver
import timber.log.Timber
import java.util.UUID

/**
 * Encapsulates profile CRUD and update business logic extracted from ProfilesViewModel.
 */
class ProfileCrudUseCase(
    private val profilesRepository: ProfileRepositoryContract,
) {
    /** Query all profiles from the repository. */
    suspend fun queryAllProfiles(): List<Profile> = profilesRepository.queryAllProfiles()

    /**
     * Create a new profile, optionally updating it with a fetch observer.
     * On failure, rolls back by deleting the created profile.
     *
     * @return The UUID of the created profile, or null if creation failed.
     */
    suspend fun createProfile(
        type: Profile.Type,
        name: String,
        source: String = "",
        ageSecretKey: String = "",
        interval: Long = 0L,
        onProgress: ((FetchStatus) -> Unit)? = null,
        onBeforeUpdate: (suspend (UUID) -> Unit)? = null,
    ): UUID? {
        var createdUuid: UUID? = null
        var updateStarted = false
        return try {
            val uuid = profilesRepository.createProfile(type, name, source, ageSecretKey, interval)
            createdUuid = uuid
            onBeforeUpdate?.invoke(uuid)
            val observer = IFetchObserver { status -> onProgress?.invoke(status) }
            updateStarted = true
            profilesRepository.updateProfile(uuid, observer)
            uuid
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Timber.e(error, "Failed to create profile")
            // 审计 1.201：只在建记录/写文件阶段失败时回滚删除；拉取订阅失败时保留记录
            // （含用户填的 URL 与备注），否则一次网络抖动就把刚填的东西全丢了，列表里可再点更新重试。
            if (!updateStarted) {
                createdUuid?.let { uuid ->
                    safeRunSilent("ProfileCrud", "Rollback profile creation: $uuid") {
                        profilesRepository.deleteProfile(uuid)
                    }
                }
            } else {
                Timber.w("Keeping profile after fetch failure: $createdUuid")
            }
            throw error
        }
    }

    /** Clone an existing profile. Returns the new UUID or null on failure. */
    suspend fun cloneProfile(uuid: UUID): UUID? = try {
        profilesRepository.cloneProfile(uuid)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to clone profile")
        null
    }

    /** Delete a profile by UUID. Returns true on success. */
    suspend fun deleteProfile(uuid: UUID): Boolean = try {
        profilesRepository.deleteProfile(uuid)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to delete profile")
        false
    }

    /** Activate a profile by UUID. Returns true on success. */
    suspend fun activateProfile(uuid: UUID): Boolean = try {
        profilesRepository.setActiveProfile(uuid)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to activate profile")
        false
    }

    /**
     * Update a single profile with download progress reporting.
     * Returns true on success.
     */
    suspend fun updateProfile(
        uuid: UUID,
        onProgress: ((FetchStatus) -> Unit)? = null,
    ): Boolean = try {
        val observer = IFetchObserver { status -> onProgress?.invoke(status) }
        profilesRepository.updateProfile(uuid, observer)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to update profile")
        false
    }

    /** 全部更新的结果：成功数与失败的订阅 UUID。审计 1.201：此前只回成功数，调用方会把部分失败也报成全部成功。 */
    data class UpdateAllResult(val updated: Int, val failed: List<UUID>)

    /**
     * Update all URL-type profiles with progress reporting.
     * Returns how many updated and which failed, so the caller can report honestly.
     */
    suspend fun updateAllUrlProfiles(
        profiles: List<Profile>,
        onProgress: ((FetchStatus) -> Unit)? = null,
    ): UpdateAllResult {
        var count = 0
        val failed = mutableListOf<UUID>()
        for (profile in profiles.filter { it.type == Profile.Type.Url }) {
            val observer = IFetchObserver { status -> onProgress?.invoke(status) }
            try {
                profilesRepository.updateProfile(profile.uuid, observer)
                count++
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Timber.e(error, "Failed to update profile ${profile.uuid}")
                failed += profile.uuid
            }
        }
        return UpdateAllResult(updated = count, failed = failed)
    }

    /** Patch profile properties. Returns true on success. */
    suspend fun patchProfile(
        uuid: UUID,
        name: String,
        source: String,
        interval: Long,
        ageSecretKey: String? = null,
    ): Boolean = try {
        profilesRepository.patchProfile(uuid, name, source, interval, ageSecretKey)
        true
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to patch profile")
        false
    }

    /** Reorder profiles by UUID list. */
    suspend fun reorderProfiles(orderedUuids: List<UUID>) {
        profilesRepository.reorderProfiles(orderedUuids)
    }

    /** Toggle a profile's enabled state. Returns the new active state or null on failure. */
    suspend fun toggleProfileEnabled(uuid: UUID): Boolean? = try {
        val profile = profilesRepository.queryProfileByUUID(uuid) ?: error("Profile not found: $uuid")
        if (profile.active) {
            profilesRepository.clearActiveProfile(profile)
            false
        } else {
            profilesRepository.setActiveProfile(uuid)
            true
        }
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.e(error, "Failed to toggle profile")
        null
    }

}
