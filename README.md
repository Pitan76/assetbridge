# Asset Bridge (Fabric/Forge)
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけを利用するMOD<br />
バージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

なお、取り込んだMODのコードは実行せず、アセットだけを利用するため、特定の機能などは動作しません。

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
本プロジェクトは、1つのコードベースから複数のMCバージョンおよびプラットフォーム（Fabric、Forge）向けのMODをビルドする構成をとっています。

### 1. マルチバージョン管理 (Stonecutter)
* Stonecutterを導入し、`versions/` 配下にバージョンごとのサブプロジェクトを展開しています。
* APIの差異はプリプロセッサコメント（`//? if >=1.20.1` など）で吸収され、ビルド時に自動生成されます。
* IDEの認識切り替えは `./gradlew "Set active project to <version>"`、コミット前は `./gradlew "Reset active project"` を実行します。
* 一括ビルドには `./gradlew chiseledBuild` を使用します。

### 2. ビルド環境
* Architectury Loomを採用し、共通のMojangマッピングを適用して開発しています（※実行時ライブラリへの依存はありません）。

### 3. 初期化設計
* Featureによるカプセル化: アセット解析やブロック・アイテム生成などの各処理は、`Feature` クラスに分割されています。
* 遅延初期化: Forgeのレジストリ凍結（`Registry is already frozen`）を避けるため、インスタンス生成処理はModコンストラクタではなく、登録イベント（`RegisterEvent` 等）の発生時まで実行が遅延されます。

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
