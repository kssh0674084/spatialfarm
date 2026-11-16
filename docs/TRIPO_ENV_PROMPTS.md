# 🌿 环境风格复刻任务包（参考图 → Tripo 生成）

目标：把你提供的吉卜力草地图"环境风格"先在游戏里做出来。
做法：**图生 3D 优先**（上传对应裁切参考图）→ 提示词微调 → 导出 GLB → 放 `tripo_inbox/`。

## 通用设置（每件）

| 项 | 设置 |
|---|---|
| 生成方式 | **Image to 3D（图生3D）**，上传 `docs/tripo_refs/` 中对应参考图 |
| 风格选择 | None / Default（不要选写实类） |
| 纹理 | 开启（质感来自贴图） |
| 对称 | 建筑/树 关；石头/花丛 可开 |
| 面数 | 10k ~ 25k |
| 导出 | **GLB**，单文件 ≤ 8MB，命名 = 下表 itemId |

**统一负向词**（图生3D 有 negative/prompt 超参时填）：
`realistic, photorealistic, dark, gritty, noise, multiple objects, text, watermark, logo, human, character, base, plinth, ground shadow`

---

## ⭐ 第一批（3 件，先做这仨，效果最明显）

### 1. 水车小屋 → `bld_watermill.glb`
参考图：`docs/tripo_refs/ref_watermill.png`（直接上传这张）

提示词：

```
cozy watermill cottage, wooden water wheel on the side, half-timber walls with cream plaster, warm terracotta clay tile roof, small round window, stone foundation, tiny chimney, charming farm village landmark building, studio ghibli inspired stylized 3D asset, hand-painted vibrant colors, saturated spring palette, soft rounded shapes, clean lowpoly game asset, white background, single building, no ground, no base, no text
```

### 2. 前景大树 → `prop_tree_big.glb`
参考图：`docs/tripo_refs/ref_tree_big.png`

提示词：

```
large old tree with thick curved trunk and wide layered leafy canopy, lush bright green foliage, gently windswept branches, studio ghibli inspired stylized 3D asset, hand-painted vibrant colors, saturated spring palette, soft rounded shapes, clean lowpoly game asset, white background, single tree, no ground, no base, no text
```

### 3. 花丛（粉紫款）→ `prop_flower_a.glb`
参考图：`docs/tripo_refs/ref_flowers.png`

提示词：

```
dense cluster of tiny meadow wildflowers in pink purple and white with soft green stems and grass blades, stylized hand-painted flower patch, studio ghibli inspired stylized 3D asset, vibrant saturated colors, soft rounded shapes, clean lowpoly game asset, white background, single flower patch, no ground, no base, no text
```

> 另做一版黄白款 → `prop_flower_b.glb`（同提示词把 `pink purple and white` 换成 `yellow and white`）

---

## 第二批（4 件）

### 4. 雪顶远山 → `env_mountain.glb`
参考图：`docs/tripo_refs/ref_mountains.png`

```
distant snowy mountain peak, blue-grey rocky slopes with clean white snow cap, stylized layered silhouette, painterly look, studio ghibli inspired stylized 3D asset, vibrant saturated colors, soft rounded shapes, lowpoly game scenery asset, white background, single mountain, no ground, no base, no text
```

### 5. 玩家小屋升级 → `bld_house.glb`
参考图：`docs/tripo_refs/ref_watermill.png`（取其建筑语言）或游戏内截图

```
charming storybook farmhouse with cream plaster walls, terracotta red gable roof, wooden arched door, flower boxes under windows, small stone chimney, studio ghibli inspired stylized 3D asset, hand-painted vibrant colors, saturated spring palette, soft rounded shapes, clean lowpoly game asset, white background, single house, no ground, no base, no text
```

### 6. 苔石 → `prop_rock.glb`
参考图：`docs/tripo_refs/ref_lake_path.png`

```
mossy rounded grey boulder with soft green moss patches, stylized hand-painted, studio ghibli inspired stylized 3D asset, vibrant colors, soft rounded shapes, lowpoly game asset, white background, single rock, no ground, no base, no text
```

### 7. 小木桥 → `prop_bridge.glb`
参考图：`docs/tripo_refs/ref_lake_path.png`

```
small wooden footbridge with gentle arch and simple railings, warm brown planks, studio ghibli inspired stylized 3D asset, hand-painted vibrant colors, soft rounded shapes, lowpoly game asset, white background, single bridge, no ground, no base, no text
```

---

## 交付流程

1. 生成 → 导出 GLB → 按 itemId 命名 → 放入 `E:\zcode\pico\tripo_inbox\`
2. 跟 Agent 说「第一批传好了」
3. Agent 自动：导入 → 替换对应物品（水车小屋放**湖边**做新地标、大树做近景、花丛换散布花、雪山换代码山）→ 截图与参考图对比验收
4. 不满意单件重生成即可

## 参考图清单（已为你裁好）

| 文件 | 内容 | 用于 |
|---|---|---|
| `docs/tripo_refs/ref_watermill.png` | 水车小屋特写 | bld_watermill / bld_house |
| `docs/tripo_refs/ref_tree_big.png` | 前景大树 | prop_tree_big |
| `docs/tripo_refs/ref_flowers.png` | 花海草地 | prop_flower_a/b |
| `docs/tripo_refs/ref_mountains.png` | 远山 | env_mountain |
| `docs/tripo_refs/ref_lake_path.png` | 湖湾石墩小路 | prop_rock / prop_bridge |
