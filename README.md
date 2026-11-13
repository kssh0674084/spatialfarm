# 小小田园 SpatialFarm 🌱

> PICO Space Program 2026 参赛作品 · Spatial 空间应用创作赛道

**A cozy island-village farming life-sim in MR.** Plant real crops, trade with the shopkeeper, upgrade your tools, expand your land, feed the basket — and keep an eye on your veggies, because your neighbor Lao Wang steals them!

把一座温馨的小岛村庄搬进你的客厅：种菜、卖菜、扩田、买工具、防小偷——玩着玩着，学会了真实的种植知识。

## ✨ 已实现玩法（可玩版本）

- 🌱 **真实种植循环**：买种子 → 播种 → 浇水（提速+品质）→ 4 阶段生长模型 → 收获入篮
- 💰 **经济闭环**：5000 元开局；种子店（8 种作物）/ 收购站（卖菜换钱）/ 工具铺（洒水喷头、肥料、稻草人）/ 扩田（2→8 块，价格阶梯 1000~32000）
- 😱 **偷菜情怀**：邻居老王定时溜达到田边偷成熟作物；立稻草人降低偷菜成功率；趁他不在家可以反偷他的菜（被逮到罚 ¥200）
- 📕 **植物图鉴**：收获解锁 8 种作物的真实种植知识卡（科普灵魂）
- 💾 **自动存档**：每 3 秒自动保存，重启进度不丢
- 🔊 **空间音频**：田园 BGM（程序化生成 16 秒循环）、树林方向鸟鸣、浇水声、收获铃
- 🌅 **开场揭示**：上岛时小岛从迷你尺寸生长展开，把你"包"进村庄
- 🦋 **活的世界**：树随风摆、小鸟绕岛飞、蝴蝶田边舞、烟囱升起炊烟
- 🌤️ **旷野之息风场景**：程序化起伏草原（RawMesh 顶点色）、渐变天空球、阳光阴影、Quaternius 3D 模型村庄

## 🎬 演示视频

[▶️ 36 秒概念预告片](docs/media/demo.mp4) —— 村庄全貌、作物生长、村庄经济、偷菜博弈、沙盘视角；**建议直接戴上模拟器体验实时手感**（水花、收获闪光、生长弹跳、夜晚氛围都会实时呈现）

## 🎮 操作方式

| 操作 | 方式 |
|---|---|
| 环顾村庄 | 转头 / 鼠标拖动 |
| 点按田垄 | 射线指向田垄按扳机（或鼠标左键）：空地播种 / 生长中浇水 / 成熟收获 |
| 点按老王菜地 | 偷菜（趁他不在家！） |
| HUD 按钮 | 🏪 种子 / 🧺 卖菜 / 📕 图鉴 / 🛠️ 工具 / 📋 委托 / 👆 照料（智能操作下一块田）/ 🪴 扩田 / 🔭 沙盘 / 🧒 孩子 |
| 关闭窗口 | 每个窗口右上角 **✕** 按钮 |

**推荐第一次游玩路线**：买 2 颗生菜种子 → 点田垄播种 → 浇 2 次水 → 等它长大（生菜 1 游戏日=60 秒）→ 收获 → 收购站卖菜 → 体验扩田。

## 🛠️ 技术栈

Android · Kotlin · Jetpack Compose（SpatialUI）· PICO Spatial SDK 6.2.8 · Stage + 4 个 WindowContainer 多窗口 · Spatial ECS · RawMesh 程序化地形 · GLB（Quaternius CC0）· 程序化音频（零版权）

## 📖 文档

- [游戏设计文档](docs/DESIGN.md)

## 📄 许可

代码 [MIT](LICENSE)；3D 素材 [Quaternius](https://quaternius.com/)（CC0）；音效由项目脚本程序化生成（CC0）。
