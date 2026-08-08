# Asset Bridge
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけを利用するMOD<br />
バージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

要件の詳細は [RD.md](RD.md) を参照。

- 対象: Minecraft 1.18.2 / Fabric・Forge（Architectury、Arch API非依存）
- 現状: MVP（ブロックのみ）

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
（例: `examplemod:foo`）登録され、クリエイティブの「建築ブロック」タブから取得できる。
外部MODのJavaコードは一切読み込まず、実行もしない。

## 処理の流れ

```text
mods/assetbridge/*.{jar,zip}
  ↓ ArchiveScanner      アセットエントリのみをメモリへ読み出し、pack.mcmetaからバージョン判定
  ↓ BlockStateParser    blockstate JSONから代表モデルを1つ抽出
  ↓ AssetVersion + AssetConverter   内部表現を経て1.18.2向けの形式へ変換
  ↓ AssetBundle         変換済みリソース + BridgedBlockAsset
  ├→ BridgedBlocks               Block / BlockItem を生成（登録はプラットフォーム側）
  └→ AssetBridgePackResources    仮想リソースパックとしてMinecraftへ提供
```

| 役割 | クラス |
| --- | --- |
| アーカイブ検出・読み出し | `archive.ArchiveScanner` / `archive.AssetArchive` |
| 内部表現 | `asset.AssetBundle` / `asset.BridgedBlockAsset` / `asset.AssetVersion` |
| 解析 | `parse.BlockStateParser` |
| バージョン変換層 | `convert.AssetConverter` / `ModelConverter` / `PassthroughConverter` |
| パイプライン | `AssetPipeline` |
| リソース提供 | `pack.AssetBridgePackResources` / `pack.AssetBridgeRepositorySource` / `mixin.PackRepositoryMixin` |
| ブロック生成 | `block.BridgedBlocks` |

ブロックの登録タイミングだけがローダーごとに異なる（Fabricはmod init、ForgeはRegistryEvent）ため、
共通コードはインスタンス生成までを担当し、登録は各プラットフォームのエントリポイントが行う。

## MVPの制限

- ブロックはプロパティ無しの単一モデルとして登録する（blockstateのvariantは代表1件のみ使用）
- ブロック挙動は石相当固定（当たり判定・硬さ・音）
- 1.18.2の `CreativeModeTab.TABS` が固定長のため、専用タブは作らず建築ブロックタブに入れる
- アイテム・エンティティ・Block Entity・GUI・レシピ・カスタムレンダリングは対象外

## ビルド

```sh
./gradlew build
```

成果物は `fabric/build/libs/` および `forge/build/libs/` に出力される。
