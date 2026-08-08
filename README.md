# Asset Bridge
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけを利用するMOD<br />
バージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

- Fabric/Forge 1.18.2

## 使い方

ゲームディレクトリに以下を作り、読み込みたいMODのJARやリソースZIPを置く。
（初回起動時にAsset Bridgeが自動生成する）

```text
mods/
└── assetbridge/
    ├── example-mod.jar
    └── assets.zip
```

`assets/<namespace>/blockstates/*.json` が見つかったブロックが、元の名前空間のまま
（例: `examplemod:foo`）登録され、クリエイティブの「Asset Bridge」タブから取得できる。
外部MODのJavaコードは一切読み込まず、実行もしない。

## 処理の流れ

```text
mods/assetbridge/*.{jar,zip}
  ↓ ArchiveScanner            アセットエントリのみをメモリへ読み出し、pack.mcmetaからバージョン判定
  ↓ AssetConverter            1.18.2向けの形式へ変換（バージョン差の吸収）
  ↓ BlockStatePropertyParser  blockstateからプロパティ（facing, half...）を逆算
  ↓ AssetBundle               変換済みリソース + BridgedBlockAsset
  ├→ BridgedBlocks               Block / BlockItem を生成（登録はプラットフォーム側）
  └→ AssetBridgePackResources    仮想リソースパックとしてMinecraftへ提供
```

blockstate JSONは**変換した上でそのまま提供する**。どのモデルをどの状態で使うかの解釈は
バニラのモデルローダーに任せ、Asset Bridgeはブロックが必要とするプロパティを
復元することに専念する。プロパティを登録できないblockstateだけ、
モデル1つの単一variantにフォールバックする。

| 役割 | クラス |
| --- | --- |
| アーカイブ検出・読み出し | `archive.ArchiveScanner` / `archive.AssetArchive` |
| パス種別 | `asset.AssetPath` |
| 内部表現 | `asset.AssetBundle` / `BridgedBlockAsset` / `BridgedStateDefinition` / `BridgedProperty` |
| 解析 | `parse.BlockStatePropertyParser` / `parse.BlockStateParser` |
| バージョン変換層 | `convert.AssetConverter` / `BlockStateConverter` / `ModelConverter` / `PassthroughConverter` |
| パイプライン | `AssetPipeline` |
| リソース提供 | `pack.AssetBridgePackResources` / `pack.AssetBridgeRepositorySource` / `mixin.PackRepositoryMixin` |
| ブロック生成 | `block.BridgedBlocks` / `block.BridgedBlock` / `block.StringProperty` |

ブロックの登録タイミングだけがローダーごとに異なる（Fabricはmod init、ForgeはRegistryEvent）ため、
共通コードはインスタンス生成までを担当し、登録は各プラットフォームのエントリポイントが行う。

## テスト

入力・解析・変換の各層はMinecraft非依存なので、通常のJUnitで検証できる。

```sh
./gradlew :common:test
```
