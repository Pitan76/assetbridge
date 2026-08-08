# Asset Bridge
取り込んだMODからブロックなどのアセットを読み込み、機能を持たず見た目だけを利用するMOD<br />
バージョンやプラットフォームが異なっていても、見た目だけでもいいから追加したい場合に使えます。

なお、取り込んだMODのコードは実行せず、アセットだけを利用するため、特定の機能などは動作しません。

- Fabric/Forge 1.18.2

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

## ビルド
```sh
./gradlew build
```

## テスト
入力/解析/変換の各層はJUnitで検証します。

```sh
./gradlew :common:test
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
