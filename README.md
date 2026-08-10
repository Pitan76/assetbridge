# Asset Bridge
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけのブロック、アイテムを利用するMOD<br />
ゲームバージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

なお、取り込んだMODのコードは実行せず、アセットだけを利用するため、特定の機能などは動作しません。<br />
Fabric/Forge 1.18.2/1.19.2/1.20.1, Fabric/NeoForge 1.21.1/26.1.2対応です。

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/assetbridge
- Modrinth: https://modrinth.com/mod/assetbridge
- ModParks: https://modparks.pitan76.net/projects/assetbridge

## 使い方
ゲームディレクトリの`mods/`に`assetbridge/`を作り、その中に読み込むMODのjarやzipを置きます。
（なお、初回起動時にAsset Bridgeが`assetbridge/`を自動で生成する）

```text
mods/
└─ assetbridge/
   ├─ aaa.jar
   ├─ bbb.jar
   └─ ccc.zip
```

`assets/<namespace>/blockstates/*.json` が見つかったブロックと、
`assets/<namespace>/models/item/*.json` が見つかったアイテムが、元の名前空間のまま
登録され、クリエイティブタブとして
「Asset Bridge: ブロック」、「Asset Bridge: アイテム」から取り出せる。

## 設定 (Config)
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
```

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
Supports Fabric/Forge 1.18.2-1.20.1 and Fabric/NeoForge 1.21.1.

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

Blocks found in `assets/<namespace>/blockstates/*.json` and items found in `assets/<namespace>/models/item/*.json` will be registered under their original namespace and can be accessed from the creative tabs "Asset Bridge: Blocks" and "Asset Bridge: Items".

## Configuration (Config)
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
```

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
