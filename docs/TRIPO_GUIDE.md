# 🎨 Tripo 模型生成任务书（小小田园）

> 你（或任何会 Tripo 的人）按本清单生成模型 → 文件交给我 → 我导入游戏并逐件校准。
> 全部模型遵守**统一风格**：吉卜力治愈卡通 · 粉彩马卡龙 · 圆润 Q 版 · 干净低模。

## 0. 通用设置（每件都按这个来）

| 项 | 设置 |
|---|---|
| 平台 | [platform.tripo3d.ai](https://platform.tripo3d.ai)（需登录；国内直连不稳，用代理访问） |
| 生成方式 | 文生 3D（Text to 3D）；有参考图时用 图生 3D 效果更好 |
| **风格后缀（每段提示词末尾加上）** | `, cute ghibli-inspired stylized cartoon, pastel macaron color palette, soft rounded shapes, clean game-ready lowpoly, single object, white background` |
| **负向词** | `realistic, photoreal, dark, gritty, noisy texture, text, logo, watermark, multiple objects` |
| 输出格式 | **GLB**（带贴图） |
| 面数 | ≤ 20,000 三角面（VR 性能） |
| 贴图 | 1024 或 2048，单文件 ≤ 8MB |
| 命名 | 按下方 **itemId** 命名，如 `bld_inn.glb` |
| 存放 | 放入 `E:\zcode\pico\tripo_inbox\`（或直接发我文件） |

> 角色额外要求：`full body character, neutral A-pose, front facing, stylized cute villager`（A-pose 便于后续绑定骨骼动画）。

## 1. 第一批：角色（最高优先 ⭐）

| itemId | 物品 | 建议提示词（主体部分） |
|---|---|---|
| `wang_lawang` | 老王（懒汉邻居） | `lazy middle-aged farmer villager wearing a straw hat and blue overalls, slightly slouching, mischievous smile` |
| `child_flower` | 小花（女儿） | `cute little girl villager with pink dress and pigtails, holding nothing, cheerful` |
| `child_yong` | 小勇（儿子） | `cute little boy villager with blue shirt and short hair, energetic` |

## 2. 第二批：地标建筑（⭐）

| itemId | 物品 | 建议提示词 |
|---|---|---|
| `bld_inn` | 胖婶食堂（客栈） | `cozy medieval half-timbered inn with warm red gable roof, small windows, wooden sign` |
| `bld_market` | 梅姨杂货摊 | `small wooden market stall with striped awning and baskets of vegetables` |
| `bld_workshop` | 锤叔作坊（铁匠铺） | `cozy blacksmith workshop with stone chimney, anvil outside, wooden beams` |
| `bld_house` | 玩家小屋 | `cozy little cottage with cream walls, coral-red roof, wooden door` |
| `bld_well` | 广场水井 | `old stone wishing well with wooden roof and bucket` |
| `bld_mill` | 风车磨坊 | `windmill on a small hill with wooden blades` |

## 3. 第三批：道具装饰（⭐）

| itemId | 物品 | 建议提示词 |
|---|---|---|
| `prop_tree_common` | 阔叶树 | `round leafy common tree with chunky foliage` |
| `prop_tree_pine` | 松树 | `cute pine tree with layered cone foliage` |
| `prop_bush` | 灌木 | `small round bush` |
| `prop_rock` | 石头 | `smooth grey boulder` |
| `prop_flower` | 花丛 | `small patch of colorful flowers` |
| `prop_barrel` | 木桶 | `wooden barrel` |
| `prop_crate` | 木箱 | `wooden crate` |
| `prop_hay` | 干草堆 | `hay bale stack` |
| `prop_cart` | 手推车 | `small wooden hand cart with two wheels` |
| `prop_path_straight` | 石板路（直） | `flat stone path tile, straight` |
| `prop_path_square` | 石板路（方） | `flat stone path tile, square` |

## 4. 第四批：作物（可选，工作量最大）

> 现有作物素材（Quaternius）质量已不错且阶段齐全；如要更换，每种作物需生成 5 个阶段**同一风格**：
> `crop_<作物>_1.glb ~ _4.glb`（生长阶段）+ `crop_<作物>_crop.glb`（成熟待收）。
> 建议**先做 2 种试水**（生菜 `lettuce`、番茄 `tomato`），验收后再扩展其余 6 种。
>
> 提示词模板：`<vegetable> plant, stage N of 4, <stage description>, in soil patch`
> 阶段描述：1=seedling sprout, 2=young plant, 3=flowering, 4=fully grown ready to harvest

## 5. 交付与验收流程

1. 你生成模型 → 下载 GLB → 按 itemId 命名 → 放入 `E:\zcode\pico\tripo_inbox\`
2. 告诉我「第 N 批传好了」
3. 我自动：导入 → 比例/朝向校准（`manifest.json`）→ 重新构建 → 模拟器截图验收
4. 每批验收截图发你确认，不合格的单件重生成即可

## 6. 参考图（图生3D用）

| 对象 | 参考图 |
|---|---|
| 整体风格 | 吉卜力乡村夕阳场景（见美术方向 A 参考） |
| 角色 | 用本任务书的英文提示词先文生3D；若想更准，找我提供按件参考图 |
| 建筑 | 现有游戏截图即可（找我截）、或 Tripo 社区同风格模型 |
