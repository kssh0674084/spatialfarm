package com.spatialfarm.game.data

/**
 * 植物图鉴 v0.2（作物阵容对齐 Quaternius crops_pack 资产：每种均有 4 个生长阶段模型）。
 * 知识点为图鉴卡背面文案，服务「玩着学会种菜」的科普定位。
 */
data class PlantDef(
    val id: String,
    val name: String,
    val emoji: String,
    val seedPrice: Int,       // 种子价（元）
    val baseSellPrice: Int,   // 保底收购价（元）
    val growDays: Int,        // 生长周期（游戏内天）
    val difficulty: Int,      // 难度 1-5 星
    val season: String,       // 适宜季节
    val knowledge: String,    // 真实种植知识（图鉴卡背面）
    val assetBase: String,    // 模型前缀：models/crops/{assetBase}_1..4 / _Crop
)

val PLANT_CATALOG: List<PlantDef> = listOf(
    PlantDef(
        id = "lettuce", name = "生菜", emoji = "🥬",
        seedPrice = 50, baseSellPrice = 120, growDays = 1, difficulty = 1,
        season = "春秋", knowledge = "播种后 30-70 天可收，怕高温，夏天要遮阳。",
        assetBase = "Lettuce",
    ),
    PlantDef(
        id = "wheat", name = "小麦", emoji = "🌾",
        seedPrice = 80, baseSellPrice = 180, growDays = 2, difficulty = 1,
        season = "秋冬", knowledge = "越冬作物，麦秆是空心的，风里摇晃也不易倒伏。",
        assetBase = "Wheat",
    ),
    PlantDef(
        id = "carrot", name = "胡萝卜", emoji = "🥕",
        seedPrice = 120, baseSellPrice = 300, growDays = 2, difficulty = 2,
        season = "秋", knowledge = "秋播为宜，土壤越疏松，根长得越直越漂亮。",
        assetBase = "Carrot",
    ),
    PlantDef(
        id = "tomato", name = "番茄", emoji = "🍅",
        seedPrice = 200, baseSellPrice = 520, growDays = 3, difficulty = 2,
        season = "春夏", knowledge = "需要打杈搭架，特别爱晒太阳。",
        assetBase = "Tomato",
    ),
    PlantDef(
        id = "corn", name = "玉米", emoji = "🌽",
        seedPrice = 250, baseSellPrice = 650, growDays = 3, difficulty = 3,
        season = "春夏", knowledge = "玉米须其实是花柱，一株只留一穗才长得饱满。",
        assetBase = "Corn",
    ),
    PlantDef(
        id = "strawberry", name = "草莓", emoji = "🍓",
        seedPrice = 400, baseSellPrice = 1000, growDays = 4, difficulty = 4,
        season = "春", knowledge = "我们吃的果肉是「假果」，真正的果实是表面的小籽。",
        assetBase = "BushBerries",
    ),
    PlantDef(
        id = "watermelon", name = "西瓜", emoji = "🍉",
        seedPrice = 300, baseSellPrice = 700, growDays = 3, difficulty = 2,
        season = "夏", knowledge = "瓜藤怕积水，起垄种植才能结出沙瓤大瓜。",
        assetBase = "Watermelon",
    ),
    PlantDef(
        id = "pumpkin", name = "南瓜", emoji = "🎃",
        seedPrice = 600, baseSellPrice = 1600, growDays = 5, difficulty = 3,
        season = "夏秋", knowledge = "一根藤上只留 2-3 个果，才能长得又大又圆。",
        assetBase = "Pumpkin",
    ),
)

fun plantById(id: String): PlantDef? = PLANT_CATALOG.find { it.id == id }
