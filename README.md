# Asset Bridge
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけのブロック、アイテムを利用するMOD<br />
ゲームバージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

なお、取り込んだMODのコードは実行せず、アセットだけを利用するため、特定の機能などは動作しません。<br />
Fabric/Forge 1.12.2/1.16.5/1.18.2/1.19.2/1.20.1, Fabric/NeoForge 1.21.1/26.1.2/26.2対応です。

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/assetbridge
- Modrinth: https://modrinth.com/mod/assetbridge
- ModParks: https://modparks.pitan76.net/projects/assetbridge

## 使い方
ゲームディレクトリの`mods/`に`assetbridge/`を作り、その中に読み込むMODのjarやzipを置きます。
（なお、初回起動時にAsset Bridgeが`assetbridge/`を自動で生成する）

```
mods/
└─ assetbridge/
   ├─ aaa.jar
   ├─ bbb.jar
   └─ ccc.zip
```

`/blockstates/*.json` と`/models/item/*.json` からブロック、アイテムとして登録します。<br />
登録されたアイテムは各MOD（名前空間）ごとのクリエイティブタブもしくは「Asset Bridge: ブロック」、「Asset Bridge: アイテム」から取り出せます。（feature.split_tab_by_namespaceでどちらかを設定する）

## 設定
`config/assetbridge.properties` でAsset Bridgeの設定をします。<br />
初回起動でconfigフォルダの中に設定ファイルが作成されます。

### assetbridge.properties

```properties
# クリエイティブタブをMOD単位で分割する（falseなら「ブロック」「アイテム」のタブの2つに）
feature.split_tab_by_namespace=true

# ブロックを登録する
feature.blocks=true

# アイテムを登録する (ブロック以外のアイテム)
feature.items=true

# ルートテーブルを生成する（ブロックなどをドロップする）
feature.loot_tables=true

# レシピを読み込む
feature.recipes=true

# リソースパックを適用する（モデルやテクスチャなど）
feature.resource_pack=true

# データパックを適用する（ルートテーブルやレシピなど）
feature.data_pack=true

# cutoutを適用する
#feature.cutout_blocks=true
feature.cutout_blocks=examplemod:example_block,examplemod:example_block2

# モデルの継承元からブロックの種類を推測し、バニラのブロックとして登録する
feature.block_kinds=true

# モデルの形状から当たり判定・選択範囲を生成する
feature.model_shapes=true

# ブリッジブロック全体のデフォルトの硬さ（破壊時間）
block_hardness=1.5

# ブリッジブロック全体のデフォルトの爆発耐性
block_resistance=6.0

# 特定のブロックだけ個別に強度を変更する場合
block_hardness.examplemod:heavy_stone=5.0
block_resistance.examplemod:example_block=30.0

# カリング（ブロックの隣接面が透けてしまう現象）を無効化する
# true (階段やチェストなど自動判定し無効化), false (無効化しない), またはカンマ区切りでブロックIDを指定
no_occlusion_blocks=true
# no_occlusion_blocks=examplemod:example_block,examplemod:example_block2
```

### ブロックの種類推測と形状生成
`feature.block_kinds` は、ブロックのモデルが継承しているバニラのモデルからそのブロックの種類を判断します。<br />
`block/stairs` を継承していれば階段、`block/slab` ならハーフブロック、という感じです。対象は階段、ハーフブロック、フェンス、塀、板ガラス、フェンスゲート、ドア、トラップドア、はしごで、それぞれバニラのブロックとして登録されるため、角の形状や設置、開閉といった挙動もバニラと同様になります。<br />
ただし、blockstateのプロパティ（名前と値）がバニラ側のブロックに無いものを含む場合は、blockstateをそのまま配信できなくなるため、通常のブロックとして登録されます。

`feature.model_shapes` は、blockstateが参照するモデルの `elements` から当たり判定・選択範囲を生成します。<br />
variantの `x` / `y` 回転にも追従し、multipartの場合は `when` の無いパーツ（＝本体）のみを使います。<br />
フルキューブのモデルは従来どおり何もしません。また `feature.block_kinds` で種類が判明したブロックは、より正確なバニラ側の形状を使うためこちらの対象外です。

## 対応アセット形式
Asset Bridgeは取り込むMODのJARに含まれる `pack.mcmeta` の `pack_format` 値や、MODメタデータ（`fabric.mod.json` / `mods.toml`）のMinecraftバージョン記述から、アセットの世代を自動判別します。

| 世代 | 対応Minecraftバージョン | リソースpack_format | データpack_format | 主な特徴 |
|------|------------------------|--------------------|--------------------|----------|
| LEGACY | 1.6 – 1.12 | 1 – 3 | — | `blocks/`・`items/` ディレクトリ、旧テクスチャ参照方式 |
| FLATTENED | 1.13 – 1.14 | 4 | — | フラットニング後、`block/`・`item/` に変更 |
| MODERN | 1.15 – 1.19.2 | 5 – 11 | 7 – 9 | 現在の基本構造。アトラス定義なし |
| ATLASES | 1.19.3 – 1.20.4 | 12 – 31 | 10 – 25 | `assets/*/atlases/` が追加された |
| COMPONENTS | 1.20.5 – 1.21.3 | 32 – 45 | 26 – 47 | アイテムスタックコンポーネント形式 |
| ITEM_DEFINITIONS | 1.21.4 / 26.x 以降 | 46 – (84+) | 48 – (101+) | `assets/*/items/` へのアイテム定義分離 |

### 変換処理の内容
- **blockstates JSON**: `normal` variantの空文字への変換、モデルパスの修正（例: `cube_all` → `block/cube_all`）
- **block/item モデル JSON**: `blocks/`, `items/` → `block/`, `item/` へのディレクトリ名変換（LEGACY世代）、将来世代の未知キーの除去、テクスチャ参照の小文字化
- **Atlas定義** (`atlases/*.json`): ランタイムパックに含める
- **アイテム定義** (`items/*.json`、ITEM_DEFINITIONS以降): ランタイムパックに含める
- **レシピ / ルートテーブル**: feature有効時にデータパックとして含める

> [!NOTE]
> `pack_format` が存在しない、読み取れないアーカイブは MODERN 世代として処理されます。

## 技術的な話
本プロジェクトは、複数のMCバージョンおよびプラットフォーム（Fabric、Forge、NeoForge）向けのMODをビルドする構成をとっています。

### クロスプラットフォーム (Architectury Loom)
- Architectury Loomを採用し、共通のMojangマッピングを適用して開発しています

### クロスバージョン管理 (Stonecutter)
- Stonecutterを導入し、`versions/` 配下にバージョンごとのサブプロジェクトを展開しています。
- APIの差異はプリプロセッサコメント（`//? if >=1.20.1` など）で吸収され、ビルド時に自動生成されます。
- IDEの認識切り替えは `./gradlew "Set active project to <version>"`、コミット前は `./gradlew "Reset active project"` を実行します。
- 全てのビルドには `./gradlew chiseledBuild` を使用します。

## ビルド
```sh
./gradlew build
```

## テスト
入力/解析/変換の各層はJUnit/GameTestで検証します。

```sh
./gradlew :common:test

./gradlew :fabric:runGametest
```

## リリース
自動的にGitHub Actionsでビルドしてリリースを作成します。

```sh
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

## ライセンス
- MIT License

## ツール
- AIエージェント: Claude Opus 5.0
- IDE: IntelliJ IDEA
- ビルドツール: Gradle

# Asset Bridge (English)
Load assets from imported mods and use them as blocks, items, etc.
Perfect for when you want to add visual elements from other mods, even across different game versions or platforms.

Note that the code from imported mods is not executed; only assets are used, so specific features will not function.
Supports Fabric/Forge 1.16.5/1.18.2/1.19.2/1.20.1 and Fabric/NeoForge 1.21.1/26.1.2.

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/assetbridge
- Modrinth: https://modrinth.com/mod/assetbridge
- ModParks: https://modparks.pitan76.net/projects/assetbridge

## Usage
Create an `assetbridge/` folder inside your game directory's `mods/` folder and place the mods you want to import inside it.
(Asset Bridge will automatically create the `assetbridge/` folder on first launch.)

```text
mods/
└─ assetbridge/
   ├─ aaa.jar
   ├─ bbb.jar
   └─ ccc.zip
```

Register blocks and items from `/blockstates/*.json` and `/models/item/*.json`.<br />
Registered items can be accessed from creative tabs for each mod (namespace) or from "Asset Bridge: Blocks" and "Asset Bridge: Items" tabs. (Set either option with `feature.split_tab_by_namespace`)

## Config
You can configure Asset Bridge in `config/assetbridge.properties`. The configuration file will be created inside the config folder on the first launch.

### assetbridge.properties

```properties
# Split creative tabs by mod (false = "Blocks" and "Items" tabs only)
feature.split_tab_by_namespace=true

# Enable block registration
feature.blocks=true

# Enable item registration (non-block items)
feature.items=true

# Generate loot tables (for dropping blocks, etc.)
feature.loot_tables=true

# Load recipes
feature.recipes=true

# Apply resource packs (models, textures, etc.)
feature.resource_pack=true

# Apply data packs (loot tables, recipes, etc.)
feature.data_pack=true

# Enable cutout
#feature.cutout_blocks=true
feature.cutout_blocks=examplemod:example_block,examplemod:example_block2

# Register a block as the vanilla block its model inherits from
feature.block_kinds=true

# Derive collision and outline shapes from the block's model
feature.model_shapes=true

# Default hardness (destroy time) for all bridged blocks
block_hardness=1.5

# Default explosion resistance for all bridged blocks
block_resistance=6.0

# Per-block hardness and resistance overrides
block_hardness.examplemod:heavy_stone=5.0
block_resistance.examplemod:example_block=30.0

# Disable culling (prevents adjacent faces from being culled)
# true (auto-detect for stairs, chests, etc.), false (do not disable), or specify block IDs in a comma-separated list
no_occlusion_blocks=true
# no_occlusion_blocks=examplemod:example_block,examplemod:example_block2
```

### Block kinds and generated shapes
`feature.block_kinds` decides what a block is from the vanilla model its own model inherits from: inheriting `block/stairs` makes it a staircase, `block/slab` a slab, and so on for fences, walls, panes, fence gates, doors, trapdoors and ladders. Those are registered as the vanilla block itself, so the corner shapes, the placement and the opening all behave the way the player expects.<br />
A blockstate that uses a property, or a value, the vanilla block does not have is registered as a plain block instead, because the file has to be served unchanged.

`feature.model_shapes` builds the collision and outline shapes from the `elements` of the models the blockstate names, following a variant's `x` / `y` rotation. For a multipart blockstate only the parts with no `when` — the body of the block — are used.<br />
Models that are full cubes are left exactly as they were, and a block recognised by `feature.block_kinds` is skipped here, since that vanilla block brings more accurate shapes.

## Supported Asset Formats
Asset Bridge automatically detects the asset generation of the imported mod's JAR from the `pack_format` value in `pack.mcmeta` or the Minecraft version declared in the mod metadata (`fabric.mod.json` / `mods.toml`).

| Generation | Minecraft Versions | Resource pack_format | Data pack_format | Notes |
|------------|-------------------|---------------------|-----------------|-------|
| LEGACY | 1.6 – 1.12 | 1 – 3 | — | Old `blocks/`/`items/` directories, legacy texture references |
| FLATTENED | 1.13 – 1.14 | 4 | — | Post-flattening; renamed to `block/`/`item/` |
| MODERN | 1.15 – 1.19.2 | 5 – 11 | 7 – 9 | Current baseline structure, no atlas definitions |
| ATLASES | 1.19.3 – 1.20.4 | 12 – 31 | 10 – 25 | Added `assets/*/atlases/` definitions |
| COMPONENTS | 1.20.5 – 1.21.3 | 32 – 45 | 26 – 47 | Item stack component format |
| ITEM_DEFINITIONS | 1.21.4 / 26.x+ | 46 – (84+) | 48 – (101+) | Item definitions split into `assets/*/items/` |

### What Gets Converted
- **blockstates JSON**: `normal` variant key renamed to empty string; model paths qualified (e.g. `cube_all` → `block/cube_all`)
- **block/item model JSON**: `blocks/`/`items/` → `block/`/`item/` (LEGACY), unknown future keys stripped, texture references lowercased
- **Atlas definitions** (`atlases/*.json`): passed through to the runtime pack
- **Item definitions** (`items/*.json`, ITEM_DEFINITIONS+): passed through to the runtime pack
- **Recipes / loot tables**: served as a data pack when the respective feature is enabled

> [!NOTE]
> Archives with no readable `pack.mcmeta` are treated as the MODERN generation.

## Technical Details
This project is structured to build mods for multiple Minecraft versions and platforms (Fabric, Forge).

### Cross-Platform (Architectury Loom)
- Uses Architectury Loom with common Mojang mappings for development.

### Cross-Version Management (Stonecutter)
-Stonecutter is used to manage version-specific subprojects under `versions/`.
- API differences are handled with preprocessor comments (e.g., `//? if >=1.20.1`) and automatically generated during build.
- To switch IDE recognition, run `./gradlew "Set active project to <version>"`. Before committing, run `./gradlew "Reset active project"`.
- Use `./gradlew chiseledBuild` for all builds.

## Build
```sh
./gradlew build
```

## Testing
Input, parsing, and transformation layers are verified with JUnit/GameTest.

```sh
./gradlew :common:test

./gradlew :fabric:runGametest
```

## Release
Automatic releases are created via GitHub Actions.

```sh
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

## License
- MIT License

## Tools
- AI Agent: Claude Opus 5.0
- IDE: IntelliJ IDEA
- Build Tool: Gradle
