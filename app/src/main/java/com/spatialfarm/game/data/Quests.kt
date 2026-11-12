package com.spatialfarm.game.data

import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDate
import kotlin.random.Random

/** 一条 NPC 委托 */
data class QuestDef(
    val id: String,
    val npc: String,
    val plantId: String,
    val count: Int,
    val reward: Int,
    var done: Boolean = false,
)

/**
 * 每日委托：按日期种子生成 3 条（每天刷新）。
 * 例：海叔想要 2 个生菜做沙拉 → 交付后获得 ¥290。
 */
object Quests {
    private val NPCS = listOf(
        "海叔" to "想拿你的%s配鱼汤",
        "胖婶" to "想把%s做成今日菜谱",
        "锤叔" to "干活时想嚼点%s",
        "梅姨" to "店里想上架新鲜%s",
    )

    /** 当日委托列表（Compose 响应式） */
    val today = mutableStateListOf<QuestDef>()

    var questDate: String = ""

    /** 确保当天的委托已生成（启动/跨天时调用） */
    fun ensureToday() {
        val todayStr = LocalDate.now().toString()
        if (questDate == todayStr && today.isNotEmpty()) return
        questDate = todayStr
        today.clear()
        val rnd = Random(todayStr.hashCode())
        val plants = PLANT_CATALOG.shuffled(rnd).take(3)
        val npcs = NPCS.shuffled(rnd).take(3)
        plants.forEachIndexed { i, plant ->
            val count = rnd.nextInt(1, 3) // 1..2
            val (npc, _) = npcs[i]
            val reward = plant.baseSellPrice * count + 50
            today.add(
                QuestDef(
                    id = "${todayStr}_$i",
                    npc = npc,
                    plantId = plant.id,
                    count = count,
                    reward = reward,
                )
            )
        }
    }

    /** 交付委托：成功返回奖励金额，失败返回 null */
    fun deliver(quest: QuestDef): Int? {
        if (quest.done) return null
        val owned = GameState.produce[quest.plantId] ?: 0
        if (owned < quest.count) return null
        // 扣除作物
        if (owned == quest.count) GameState.produce.remove(quest.plantId)
        else GameState.produce[quest.plantId] = owned - quest.count
        GameState.earn(quest.reward)
        quest.done = true
        val def = plantById(quest.plantId)
        GameState.postHint(
            "✅ 帮${quest.npc}交付了 ${quest.count} 个${def?.emoji ?: ""}${def?.name ?: ""}，+¥${quest.reward}！"
        )
        return quest.reward
    }
}
