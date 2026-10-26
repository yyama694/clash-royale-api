---
name: balance-update
description: クラロワの新しいバランス調整を、サイトのバランス調整画面(`/balance`)に載せる。公式の発表を探し(公式ブログに無ければ公式XのArticle)、`balance/changes.tsv`に写し、新しい項目名を10言語で裏付けて足し、テスト・デプロイし、Xで知らせる材料をまとめる。「バランス調整があった」「今日もバランス調整あったよ」「バランス調整を画面に載せて」「調整内容をメモして」など、新しいバランス調整の反映を頼まれたら使う。公式投稿への返信だけなら`x-official-reply`、単独の投稿だけなら`x-post`を使う。
---

# バランス調整を画面に載せる(princess-tower)

2026-10-20に、10/19のシーズン途中の調整を載せたときの手順をまとめたもの(進捗ログ1.186)。画面の仕様は`画面一覧.md`の「バランス調整画面」、訳語の裏付けは`ハマりどころ.md`の「訳語・用語の裏付け」。

## 1. 公式の発表を探す

いちばん時間がかかるところ。**写すのは必ず公式から**(RoyaleAPIなどのまとめは、変化率や分類が公式と違うことがあった。10/19はホバリング砲を+5%(公式+4%)、スケルトンを「不具合の修正」(公式はリワーク)としていた)。

1. 公式ブログの一覧 `https://supercell.com/en/games/clashroyale/blog/` を見る(WebFetchで読める)。月初めの調整は`release-notes/<月>-balance-changes-<年>/`に載る。
2. **ブログに無ければ公式XのArticle**。シーズン途中の調整(10/19)はブログに載らず、Xの記事機能(Article)だけだった。Chromeで自分のタブを作り、`https://x.com/ClashRoyale`(英語)と`https://x.com/ClashRoyaleJP`(日本語)の投稿を読む。記事本文は`[data-testid=twitterArticleRichTextView]`の`innerText`で取れる。
3. 各国語の公式アカウント(@ClashRoyaleES・@ClashRoyaleKRなど)は2022〜23年から止まっている。公式の訳があるのは英語と日本語だけ。
4. 実施日(UTC)を決める。記事の公開時刻と「すでに適用済み」などの文言から。調整前の使用率は「実施日の0時(UTC)より前の最後の集計」になる。

## 2. `changes.tsv`に写す

`src/main/resources/balance/changes.tsv`(書式はファイルの先頭)。**コードの変更なのでworktreeで作業する**(CLAUDE.md)。

- `update<TAB>実施日<TAB>英語版のURL`。日本語版があれば`<TAB>ja=<URL>`を足す(日本語の画面からはそちらへ「公式の発表」としてリンクする)。
- `change`は記事の順(弱体化→強化→リワーク→不具合の修正の中で、記事に出てくる順)。カード名は公式APIの英語名(`messages_ja.properties`の`card.<英語名>`で確かめる)。形は`normal`/`evolution`/`hero`。
- 数値と変化率は**公式の表記のまま**(計算し直さない)。秒は`5s`、マスは`0.5tiles`。数値の無い変更は項目名だけの5列。

## 3. 新しい項目名を10言語で足す

`messages*.properties`の`balance.stat.<項目>`。英語に無いと`MessagesConsistencyTest`が落ちる。

1. 日本語は、公式の日本語版(XのArticle)があればその表記をそのまま使う。
2. 他の言語は、ゲームデータの`texts.csv`(過去のセッションのscratchpadの`gamedata/texts.csv`、2025年10月版。無ければ`HastyarSeamand/csv_logic-for-CR`)で探す。`TID_SPELL_ATTRIBUTE_*`・`TID_ABILITY_STAT_*`・`TID_BUFF_*`に項目名がある。英語と日本語の両方が一致する行なら、その行の各言語の値を使える。
3. 無ければ公式の語(ダメージ・効果時間・スキルなど)を組み合わせて訳す。英語のままにはしない(10/17にユーザー指示)。カードの説明文(`TID_SPELL_INFO_*`)の各言語と語が合うかも見る。
4. `texts.csv`の検索は、Pythonのスクリプトをscratchpadに**Writeで**書き、`python -I`で実行する(ヒアドキュメントで渡すと`\b`などが壊れる)。出力は`sys.stdout.reconfigure(encoding="utf-8")`にする(cp932で落ちる)。
5. 新しい単位が要るときは`ViewMapper.balanceValue`に足す(マスは`balance.unit.tiles`)。

## 4. 確かめてデプロイ

1. `mvn test`(全件)。
2. ローカルで起動して実描画を確かめる。集計データが無いと使用率は出ないが、項目名・単位・画像・リンクは確かめられる。使用率の表示も見たいときは、手元の毎週のバックアップ(`C:\dev\backup\clash-royale-api\player-index-*.tar.gz`の`card-usage/`)をscratchpadに取り出して`PLAYER_INDEX_DIR`に渡す。起動は`CARD_USAGE_ENABLED=false RANKING_HISTORY_ENABLED=false SERVER_PORT=18080 mvn -q spring-boot:run`(集計で公式APIを1000回呼ばないように止める)。止めるときはmvnだけでなく子のjavaも止める。
3. masterにマージ → masterで`mvn clean package` → push → デプロイ(CLAUDE.mdの手順。デッキ集計が動いていないことをログで確かめる)。
4. 本番の`/balance/<実施日>`を開き、使用率(調整前と今)が出ているかを確かめる。調整の当日は「今」の集計がまだ調整前のことがあり、その場合は使用率の行が出ない(次の集計で出る)。

## 4.5 カード詳細画面のステータスを撮り直す

カード詳細画面の「ステータス」は、ゲームの画面から読み取った値(`cards/stats.tsv`)で、調整の前の値のまま残る(画面には「〇〇時点のゲーム内の値です」と読み取った日が出る)。調整があったら、対象のカードだけ`card-stats-capture`スキルで撮り直し、表を作り直してデプロイする。PC版のクラロワをユーザーに起動してもらう必要があるので、するかはユーザーに確認する。

## 5. 記録とXの材料

- `進捗ログ.md`に1フェーズ、気づいたハマりは`ハマりどころ.md`、仕様が変わったら`画面一覧.md`。
- 画面の「まとめ」(種類ごとの平均と、いちばん変わったカード)と、1日後・3日後・7日後の推移が、Xの速報のネタになる(投稿の型は`Xポスト案.md`の案34、リンクは`/balance/<実施日>?lang=ja&from=x`)。投稿は`x-post`スキル、公式投稿への返信は`x-official-reply`スキルで行う。投稿するかはユーザーに確認する。
