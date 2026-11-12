package com.spatialfarm.game.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue

/**
 * 全局游戏状态（Compose 响应式单例）。
 * M2 阶段先内存态，M3 引入 JSON 存档。
 */
object GameState {
    /** 钱包（元）。初始 5000 元是设计定值。 */
    var wallet by mutableIntStateOf(5000)
        private set

    /** 背包种子：plantId -> 持有数量 */
    val seeds = mutableStateMapOf<String, Int>()

    /** 购买种子；成功返回 true。 */
    fun buySeed(plant: PlantDef): Boolean {
        if (wallet < plant.seedPrice) return false
        wallet -= plant.seedPrice
        seeds[plant.id] = (seeds[plant.id] ?: 0) + 1
        return true
    }

    /** 持有某种子数量 */
    fun seedCount(plantId: String): Int = seeds[plantId] ?: 0

    /** 收入入账（M2-4 卖菜用）。 */
    fun earn(amount: Int) {
        require(amount >= 0)
        wallet += amount
    }
}
