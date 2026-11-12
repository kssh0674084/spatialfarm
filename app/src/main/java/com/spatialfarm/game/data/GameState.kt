package com.spatialfarm.game.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局游戏状态（Compose 响应式单例）。
 * M2 阶段先内存态，M3 引入 JSON 存档。
 */
object GameState {
    /** 钱包（元）。初始 5000 元是设计定值。 */
    var wallet by mutableIntStateOf(5000)
        private set

    /** 已拥有田块数（初始 2 块免费） */
    var plotsOwned by mutableIntStateOf(2)
        private set

    /** 扩田价格阶梯（第 3 块起） */
    private val PLOT_PRICES = listOf(1000, 2000, 4000, 8000, 16000, 32000)

    /** 下一块田价格，全买完返回 null */
    fun nextPlotPrice(): Int? = PLOT_PRICES.getOrNull(plotsOwned - 2)

    /** 购买下一块田：成功返回新田序号（从 0 起），失败返回 null */
    fun buyNextPlot(): Int? {
        val price = nextPlotPrice() ?: run {
            hint = "所有田地都买齐啦！🏆"
            return null
        }
        if (wallet < price) {
            hint = "扩田要 ¥$price，钱还不够，多种点菜吧 💰"
            return null
        }
        wallet -= price
        val idx = plotsOwned
        plotsOwned++
        hint = "买下第 $plotsOwned 块田！荒地变良田 🎉"
        return idx
    }

    /** 背包种子：plantId -> 持有数量 */
    val seeds = mutableStateMapOf<String, Int>()

    /** 收获的作物：plantId -> 数量（M2-4 收购站卖钱） */
    val produce = mutableStateMapOf<String, Int>()

    /** 图鉴解锁记录：plantId -> 累计收获次数（卖出不影响解锁） */
    val harvestLog = mutableStateMapOf<String, Int>()

    /** 当前选中的种子 plantId */
    var selectedSeedId by mutableStateOf<String?>(null)
        private set

    /** 最近一条操作提示（HUD 显示用） */
    var hint by mutableStateOf("先买种子，再点田垄播种 🌱")

    /** 购买种子；成功返回 true。 */
    fun buySeed(plant: PlantDef): Boolean {
        if (wallet < plant.seedPrice) {
            hint = "钱不够啦，去卖点菜吧 💰"
            return false
        }
        wallet -= plant.seedPrice
        seeds[plant.id] = (seeds[plant.id] ?: 0) + 1
        if (selectedSeedId == null) selectedSeedId = plant.id
        hint = "买了 1 颗${plant.name}种子，点田垄播种吧"
        return true
    }

    /** 持有某种子数量 */
    fun seedCount(plantId: String): Int = seeds[plantId] ?: 0

    /** 选中种子 */
    fun selectSeed(plantId: String) {
        selectedSeedId = plantId
        hint = "已选 ${plantById(plantId)?.name ?: plantId} 种子，点空地播种"
    }

    /** 当前选中的植物定义 */
    fun selectedPlant(): PlantDef? = selectedSeedId?.let { plantById(it) }

    /** 消耗一颗种子；成功返回 true。 */
    fun consumeSeed(plantId: String): Boolean {
        val n = seeds[plantId] ?: return false
        if (n <= 0) return false
        if (n == 1) {
            seeds.remove(plantId)
            if (selectedSeedId == plantId) selectedSeedId = seeds.keys.firstOrNull()
        } else {
            seeds[plantId] = n - 1
        }
        return true
    }

    /** 收入入账（卖菜用）。 */
    fun earn(amount: Int) {
        require(amount >= 0)
        wallet += amount
    }

    /** 收获入篮 */
    fun addProduce(plantId: String, count: Int = 1) {
        produce[plantId] = (produce[plantId] ?: 0) + count
        harvestLog[plantId] = (harvestLog[plantId] ?: 0) + count
    }

    /** 卖出作物：成功返回收入（元）。count 超过持有量时卖出全部。 */
    fun sellProduce(plantId: String, count: Int = 1): Int {
        val def = plantById(plantId) ?: return 0
        val owned = produce[plantId] ?: return 0
        val n = count.coerceAtMost(owned)
        if (n <= 0) return 0
        if (n >= owned) produce.remove(plantId) else produce[plantId] = owned - n
        val income = def.baseSellPrice * n
        earn(income)
        return income
    }

    /** 全部卖出：返回总收入 */
    fun sellAll(): Int {
        var total = 0
        for (id in produce.keys.toList()) {
            total += sellProduce(id, produce[id] ?: 0)
        }
        return total
    }

    /** 图鉴是否解锁 */
    fun isUnlocked(plantId: String): Boolean = (harvestLog[plantId] ?: 0) > 0

    /** 收成总量 */
    fun produceTotal(): Int = produce.values.sum()

    /** 更新 HUD 提示 */
    fun postHint(text: String) {
        hint = text
    }
}
