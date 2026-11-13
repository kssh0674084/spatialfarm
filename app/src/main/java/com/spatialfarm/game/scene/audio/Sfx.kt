package com.spatialfarm.game.scene.audio

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.Directivity
import com.pico.spatial.core.ecs.audio.DistanceAttenuationMode
import com.pico.spatial.core.ecs.resource.AudioResource

/**
 * 空间音效管理（程序化生成 WAV，零版权）。
 * - 鸟鸣环境音：在树林处循环（接受位置衰减，越靠近树林越响）
 * - 收获铃 / 浇水声：在田垄位置一次性播放（点位音源）
 */
object Sfx {
    private var birdsRes: AudioResource? = null
    private var harvestRes: AudioResource? = null
    private var waterRes: AudioResource? = null
    private var bgmRes: AudioResource? = null

    private fun ensureLoaded() {
        if (birdsRes != null) return
        birdsRes = AudioResource.load("sfx_birds", "asset://audio/birds.wav", LoadType.FROM_ASSETS)
            .also { it.toGlobal() }
        harvestRes = AudioResource.load("sfx_harvest", "asset://audio/harvest.wav", LoadType.FROM_ASSETS)
            .also { it.toGlobal() }
        waterRes = AudioResource.load("sfx_water", "asset://audio/water.wav", LoadType.FROM_ASSETS)
            .also { it.toGlobal() }
        bgmRes = AudioResource.load("sfx_bgm", "asset://audio/bgm.wav", LoadType.FROM_ASSETS)
            .also { it.toGlobal() }
    }

    /** 给实体挂上点位音源组件（一次性创建） */
    fun attachPointSource(entity: Entity, volume: Float = 0.6f) {
        if (entity.components.has(ObjectAudioComponent::class.java)) return
        entity.components.set(
            ObjectAudioComponent(
                volume = volume,
                Directivity(pattern = 0.5f, sharpness = 0.3f),
                distanceAttenuationMode = DistanceAttenuationMode.INVERSE_SQUARED,
                reverbVolume = 0.2f,
            )
        )
    }

    /** 播放环境鸟鸣（由循环调度器重复调用） */
    fun playBirdAmbient(entity: Entity) {
        runCatching {
            ensureLoaded()
            val res = birdsRes ?: return
            entity.playAudio(res).play()
        }
    }

    fun playHarvest(entity: Entity) {
        runCatching {
            ensureLoaded()
            val res = harvestRes ?: return
            entity.playAudio(res).play()
        }
    }

    /** 播放田园 BGM（32 秒循环，由调度器重复调用） */
    fun playBgm(entity: Entity) {
        runCatching {
            ensureLoaded()
            val res = bgmRes ?: return
            entity.playAudio(res).play()
        }
    }

    fun playWater(entity: Entity) {
        runCatching {
            ensureLoaded()
            val res = waterRes ?: return
            entity.playAudio(res).play()
        }
    }
}
