# プロジェクト設定: Clash Royale API + Spring Boot + OCI デプロイ

このファイルは本プロジェクト(Clash Royale公式APIを使ったWebアプリのOCIデプロイ)専用の作業方針です。
共通の作業方針はグローバルのCLAUDE.mdを参照。個別ルールが競合する場合は本ファイルを優先する。
基本構成・進め方は `C:\dev\hello-world` プロジェクトを踏襲しているが、下記の点は本プロジェクト用に変更している。

## 進め方(hello-worldからの変更点)

- ユーザーはhello-worldプロジェクトでSpring Boot + OCIデプロイを一通り経験済みのため、hello-worldで採用した「1作業ごとに事前説明+確認」という細かい進め方は本プロジェクトでは採用しない。
- 通常セッションと同等の粒度で進めてよい。ただし下記のような大きな判断・不可逆な操作は引き続き事前確認する(グローバル方針・セッション方針と同様)。
  - OCIインフラの作成・削除
  - GitHubリポジトリ作成
  - 課金が発生し得る操作
  - 既存データ・設定の削除や上書き
- 専門用語が出た際に簡単な補足を添える方針(グローバルCLAUDE.md)は本プロジェクトでも引き続き適用する。
- 確認は自由入力ではなく選択肢形式(AskUserQuestion)で行う(hello-worldでの決定を踏襲)。
- **例外**: `CLAUDE.md`など`.md`ファイルへの進捗状況の記入・更新は、事前確認なしで行ってよい(2026-09-13にユーザーから明示指示。hello-worldでの運用を踏襲)。実際のコード変更・コマンド実行・git操作・外部サービスへの操作(OCI操作、GitHubへのpushなど)は引き続き大きな判断のみ事前確認する。
- **例外**: `git push`とVMへのデプロイ(scp転送・systemctl restart)は、事前確認なしで行ってよい(2026-09-14にユーザーから明示指示。理由: このアプリはまだリリース前であり、pushやデプロイの失敗が実害に繋がらないため)。**アプリを正式リリースした後は、この例外は見直す想定**(リリース後は本番影響があるため、都度確認に戻すか要検討)。GitHubリポジトリ作成やOCIインフラの作成・削除など、他の大きな判断は引き続き事前確認する。
- **作業フォルダの分離(2026-09-19にユーザーが決定)**: コードを変更するときは、**他のセッションの有無にかかわらず常に**git worktreeと作業ブランチで作業する。masterの作業ツリー(`C:\dev\clash-royale-api`)では直接コードを編集しない。
  - 理由: 同じ作業ツリーを複数セッションで共有すると、他セッションの書きかけの変更がテスト・コミット・jarに混ざる(2026-09-19に実際に発生)。他セッションは後から始まることもあり、開始時点では並行に気づけないため、「並行するときだけ」ではなく「常に」とした。
  - worktreeはClaude CodeのEnterWorktreeで`.claude/worktrees/`配下に作る(`.gitignore`対象)。`config/application-local.yml`はgit管理外なので、ローカル起動が必要ならworktreeにコピーする。
  - **例外**: `.md`だけの変更(進捗ログ・TODOなどの記録)は、masterに直接書いてコミットしてよい。コミットでは自分が変えたファイルだけを指定する。
  - 作業が終わったら、次の順で進める: worktreeでテスト → masterにマージ → masterでもう一度テスト → push → デプロイ → worktreeとブランチを削除。
  - **デプロイはmasterのコミット済みの状態からだけ行う**。デプロイの前に`git worktree list`で作業中の他のworktreeを確認し、他セッションと同時にVMを再起動しない。
  - 以前のルールの経緯: 2026-09-15に「調査系セッションは自分で編集せず別セッションに依頼する」を一時導入し、同日撤回した(`進捗ログ.md`参照)。

## プロジェクト概要

- 目的: Clash Royale公式API(developer.clashroyale.com)を利用し、Clash Royaleユーザー向けにプレイヤー情報・クラン情報などを閲覧できるWebアプリを提供する。学習(Spring Boot実践)と実用を兼ねる。
- **収益化(広告表示)も目的の一つ(2026-09-21にユーザーが明言)**: 「広告は入れる方向で進める。そのためのサイトでもある」。学習・実用に加えて**収益化が第3の目的**になった。広告導入は決定事項であり、「入れるかどうか」はもう議論しない。機能追加・宣伝の優先度を判断するときは、ページビュー・回遊・広告単価への影響も加味してよい。実行計画は`収益化考え中.md`の「広告導入ロードマップ」を参照。
  - **ドメイン名・Xアカウント名の商標問題 → 2026-09-21に解消済み**。旧ドメイン`clashroyale-api.duckdns.org`と旧Xアカウント`@clashroyal888`(表示名「Clash Royale Search」)はSupercellの商標を含みファンコンテンツポリシーに抵触していたため、**DuckDNS内で`princess-tower.duckdns.org`に改名**(0円)、Xも表示名「princess tower」・ハンドル`@princess_tower8`に変更し、過去投稿は全削除した。詳細は`進捗ログ.md`のフェーズ1.50を参照。
    - 独自ドメインの取得は必須ではないと判明済み(2026-09-21に一次情報で確認。`duckdns.org`はPublic Suffix List登録済みでAdSense公式ヘルプが「サイト」として追加できるカテゴリに該当)。根拠と出典は`収益化考え中.md`の「ドメイン問題の再整理と調査結果」を参照。
    - **AdSense審査の本当の関門はコンテンツ**(「ユーザーの興味を引く独自のコンテンツ」が要件)。当サイトはAPI結果を表示する機能サイトで読ませる文章がほぼ無いため、ここが唯一かつ最大の課題。
- **サービスの方向性(2026-09-19にユーザーが変更)**: 全世界のクラロワユーザーに向けて発信する。2026-09-18に決めた「日本のユーザーに特化する」方針と、それを前提にした施策(日本語表示を差別化ポイントにする、日本語コミュニティへの告知など)は同日すべて撤回した。機能や施策を考えるときは、特定の国・言語に寄せず全世界の利用者を前提にする。RoyaleAPIのような大手のデッキ統計サイトと、データ量と基盤の規模で張り合わない点は引き続き有効。詳細と、方針変更で見直しが必要な項目は`TODO.md`の「サービスの方向性」を参照。
- 最初のマイルストーンは最小機能(プレイヤー検索・クラン検索)とし、その後クイズ機能など追加サービスを継続的に足していく方針(2026-09-13時点でユーザーが表明)。
- ソース管理: GitHub(このディレクトリでgit初期化 → GitHubリポジトリにpush、hello-worldと同様の手順)。
- デプロイは当面手動(SSH接続してjarを配置・再起動)。CI/CD自動化は将来検討。

## 提供機能(段階的に追加)

- **フェーズ1(最小機能、実装済み)**: 画面ごとの詳細は`画面一覧.md`を参照。
  - プレイヤー検索(タグ・名前の両対応。名前は当サイトが確認したプレイヤーのみ、完全一致は全員をページ送りで・前方一致は1ページ50人の残りの枠で表示): 連勝・連敗、ランク戦の順位、使用中のデッキ(ゲーム内で今セットしているもの)、戦績サマリー・得意/苦手カード・直近対戦履歴、対戦詳細(両者のデッキ・タワーユニット)。デッキには平均エリクサー・4枚サイクル・平均カードレベルとゲームへのコピーボタン。対戦履歴の相手にも平均カードレベル
  - カード一覧画面(`/cards`、ヘッダーのメニューからリンク): レアリティ別・エリクサー順。表示言語の名前/英語名で絞り込み(日本語はひらがな可、西・葡はアクセント記号なしでも可)と、エリクサーコストでの絞り込み(2026-09-20に追加。`進捗ログ.md`のフェーズ1.36)
  - カード詳細画面(`/card/{id}`): 画像(進化があれば2枚)・レアリティ・エリクサー・レベル範囲。対戦詳細や得意/苦手カードのカード画像からリンク
  - カードコレクション(プレイヤー情報画面): レアリティ別の「最大レベルまで上げた枚数/総枚数」。公式APIが未所持カードを判別できないため所持率ではなく最大レベル率を出している(`進捗ログ.md`のフェーズ1.30・1.33)
  - クラン検索(タグ・クラン名の両対応): メンバー一覧(役職・トロフィー・寄付数・最終アクセス、列ごとのソート)
  - クランランキング画面(`/ranking`、トップページからリンク): グローバル/国・地域別をタブで切替。対象国はAccept-Language推定+Cookie保存
  - 個人ランキング: トップページに上位100人(2026-09-20に10人から変更。経緯は下の「開発方針」節を参照)、個人ランキング画面(`/ranking/players`)に上位1000人(ランク戦の現在シーズン。ランク戦は2025年6月に公式がパス・オブ・レジェンドから改名したもの。1000人は公式APIが返せる上限)。どちらもグローバル/国・地域別をタブで切替
  - クランランキング: クランランキング画面(`/ranking`)に上位1000クラン(2026-09-19に1000件まで拡大。経緯は`進捗ログ.md`のフェーズ1.29)。上位20件にはクラン対戦トロフィーも表示(上位はクランスコアが上限で同点になるため。フェーズ1.31)
  - 日本語/英語/スペイン語/ポルトガル語/ドイツ語/フランス語/イタリア語/ロシア語の8言語で表示切替(2026-09-19に西・葡、続けて独・仏・伊・露を追加。ポルトガル語はゲーム内と同じブラジル向けの訳。詳細は`進捗ログ.md`のフェーズ1.20・1.28)
  - お気に入り(プレイヤー・クラン、各10件まで。DBは使わずCookieで保持): プレイヤー情報画面・クラン情報画面の☆ボタンで登録・解除、トップページに名前だけのカード(公式APIは呼ばない)、お気に入り画面(`/favorites`、ヘッダーのメニューからリンク)で最新情報の一覧・解除。詳細は`お気に入り（検討中）.md`と`進捗ログ.md`のフェーズ1.37・1.38・1.39
- **フェーズ2以降(未着手・アイデア段階)**: クラロワクイズなど、ユーザーが継続的に遊べる追加コンテンツ。着手時にこのセクションを更新する。

## 画面一覧

画面名の用語集は`画面一覧.md`(プロジェクトルート)を参照。ユーザーとのコミュニケーションではこのファイルの名称で統一する。

## 技術スタック

- 言語/フレームワーク: Java 21 (LTS) + Spring Boot 4.1.x
  - 2026-09-15にSpring Boot 3.3.4から4.1.1へ更新。3.3系・3.5系ともOSSサポートが終了しておりセキュリティパッチが届かないため。
  - **Boot 4での注意点**: `RestClient.Builder`の自動設定は`spring-boot-starter-restclient`に分離されている。この依存が無いとDIに失敗する。
- ビルドツール: Maven
- 画面: Thymeleaf(サーバーサイドレンダリング。hello-worldと同じ方針)
  - 表示用ラベルはすべて`messages.properties`(英語)と`messages_ja` / `_es` / `_pt` / `_de` / `_fr` / `_it` / `_ru`の各`.properties`(計8言語分)に集約。テンプレートからの`T(...)`によるstaticメソッド呼び出しは使わない。
  - 表示言語は`?lang=ja` / `en` / `es` / `pt` / `de` / `fr` / `it` / `ru`で切替(Cookie保持)。未指定時はAccept-Language、それも無い・対応言語が無ければ英語(2026-09-19に全世界向けへの方針変更に合わせ、無指定時の既定を日本語から英語に変更)。対応言語の一覧は`SupportedLanguages.SUPPORTED`の1か所だけで、言語切替リンク・hreflang・テストはそこから作る。
  - 言語を足すときは、カード名などゲーム内の用語を公式の表記で裏付ける(西・葡での方法は`進捗ログ.md`のフェーズ1.20)。
- DB: **当面なし**。まずはClash Royale APIの呼び出し結果をそのまま画面に表示する構成で開始し、キャッシュやクイズデータの保存が必要になった段階でPostgreSQL導入を検討する(2026-09-13時点でユーザーが決定)。
- Webサーバー: **2026-09-20にApache(httpd)をリバースプロキシとして導入**(それまではhello-worldと同様、組み込みTomcatを直接公開)。Apache→Tomcat(127.0.0.1:8080)へ`mod_proxy`/`mod_proxy_http`でプロキシする構成。8080番は外部非公開(firewalld・OCIセキュリティリストとも削除済み)で、アプリへはApache経由のみでアクセスする。詳細は`進捗ログ.md`のフェーズ1.41を参照。
- **ドメイン名**: `princess-tower.duckdns.org`(DuckDNSの無料サブドメイン。**2026-09-21に商標問題解消のため`clashroyale-api.duckdns.org`から改名**、詳細は`進捗ログ.md`のフェーズ1.50)。本番VMの固定IP(`161.33.136.175`)を指す。DuckDNS側の更新(IPが変わった場合の反映)はユーザーがDuckDNSのサイトで行う運用。旧ドメインはDuckDNS側で削除済み(301リダイレクトなし)、証明書・Apache vhostとも撤去済み。
- **HTTPS化(2026-09-20にLet's Encrypt/certbotで対応、2026-09-21に新ドメインへ移行)**: `mod_ssl` + `certbot`(EPEL経由。Oracle Linux 9は`oracle-epel-release-el9`を導入後、`ol9_developer_EPEL`リポジトリを有効化して`certbot`/`python3-certbot-apache`を取得)。`certbot --apache`で証明書取得とApache設定の自動編集(`/etc/httpd/conf.d/princess-tower-proxy-le-ssl.conf`を生成)。80番はリダイレクト用に残し、443番を新たに公開(firewalld・OCIセキュリティリストとも追加)。
  - ⚠ **`certbot --apache`は新規vhost生成時、直前に作ったHTTP用vhostの`RequestHeader set X-Forwarded-Proto`をそのまま複製する**(HTTPS用なのに`"http"`のままになる)。2026-09-20の初回HTTPS化時と2026-09-21のドメイン移行時の両方で発生。**証明書取得後は必ずSSL vhostの当該行を`"https"`に手動修正すること**(OGP/hreflang/sitemapの絶対URLが`http://`になるバグの原因)。
  - **証明書の自動更新**: `certbot-renew.timer`(1日2回起動、期限30日前から更新)。**インストール直後は`disabled`だったため`systemctl enable --now`で有効化した**(certbotのインストーラーが有効化まではしない点に注意。次回別ドメインでHTTPS化する際も確認すること)。2026-09-21のドメイン移行後も`enabled`/`active`を再確認済み。
  - 証明書は`/etc/letsencrypt/live/princess-tower.duckdns.org/`配下(有効期限90日、2026-09-21取得分は2026-12-20まで)。

## Clash Royale API連携に関する注意点

- APIキーは2026-09-13に取得済み(developer.clashroyale.comでSupercell IDログインして発行)。
- **重要な制約**: Clash Royale公式APIのキーは、アクセス元のIPアドレスを事前に登録(ホワイトリスト)する方式。
  - 本番(OCI VM: `161.33.136.175`。2026-09-20に固定IP(予約済みパブリックIP)取得に伴い変更)用と、ローカル開発用とでIPアドレスが異なる点に注意。2026-09-20にキーを作り直し、本番の新IPとローカル開発機のIPの2つを許可リストに登録した(旧VM`132.226.7.203`はもう使わないため今回の許可リストからは外した)。**新トークンは本番(`/etc/clash-royale-api/env`)・ローカル(`config/application-local.yml`)の双方に反映済み**(2026-09-21に両者のトークンが一致すること、本番の新IPから公式APIの呼び出しが成功していることで確認)。
  - ローカル開発機のグローバルIPは固定でない可能性があるため、開発中にAPIが403エラーになった場合はIPアドレスが変わっていないか、developer.clashroyale.com側のキー設定を確認する。
  - Allowed IP Addressesは作成後の編集(追加)が不可のため、IPを追加したい場合はキーの作り直しが必要(詳細は`進捗ログ.md`参照)。
- APIキー(トークン)は絶対にリポジトリにコミットしない。**jarにも焼き込まない**(2026-09-15に対応)。
  - ローカル: `./config/application-local.yml`(`src/main/resources`の外、`.gitignore`対象)。サンプルは`config.example.yml`。`mvn spring-boot:run`で自動的にlocalプロファイルが有効になる。
  - 本番: 環境変数`CLASHROYALE_API_TOKEN`。VM上の`/etc/clash-royale-api/env`(root:root 600)をsystemdの`EnvironmentFile`で読み込む。
  - トークン未設定のまま起動すると`ClashRoyaleApiProperties`の`@NotBlank`で起動時に失敗する(403を出し続けるより原因が分かりやすいため)。
- 呼び出し回数制限: 429応答は`ApiRateLimitException`で専用のエラー表示にし、Caffeineキャッシュで呼び出し数自体も抑えている。具体的な制限値は未確認(判明したら追記する)。

## インフラ構成(OCI)

- hello-worldプロジェクトで作成したOCI VM(VM.Standard.E2.1.Micro、`140.245.83.216`)は2026-09-13時点でユーザーの意向により削除し、本プロジェクト用に新規VMを作成済み(ローカルの`C:\dev\hello-world`フォルダ・GitHubリポジトリ自体は学習記録としてそのまま残す)。
- **現行VM(2026-09-20〜)**: パブリックIP: `161.33.136.175`(2026-09-20に固定IP(予約済みパブリックIP)を取得。以前は`158.179.182.210`の一時IPで、インスタンス再起動などで変わる可能性があったが、固定IP化により今後は変わらない見込み)。**VM.Standard.A1.Flex**(ARM、Always Free枠。1 OCPU・約5.5GBメモリ)。リージョン: 東京(ap-tokyo-1)。OS: Oracle Linux Server 9.8(aarch64)。SSH鍵: `oci_clash_royale_api`(旧VMと共通)。Javaはdnfの`java-21-openjdk-headless`(aarch64、Oracle Linux 9標準リポジトリ)を使用(旧VMのAmazon Corretto x86_64から変更)。詳細な移行手順は`進捗ログ.md`のフェーズ1.40を参照。
- **旧VM(2026-09-13〜2026-09-20、2026-09-21に削除済み)**: パブリックIP: `132.226.7.203`。x86_64の**VM.Standard.E2.1.Micro**(Always Free枠。作成時は"Out of host capacity"でAmpere A1.Flexが取れず、E2.1.Microで作成していた)。新VMへの移行漏れが無いことを確認したうえで、2026-09-21にブートボリュームごと削除した(経緯と確認手順は`進捗ログ.md`のフェーズ1.53)。**現在OCI上に存在するインスタンスは新VM(`clash-royale-api2`)の1台のみ**。
- ネットワーク公開・ポート開放・ファイアウォール設定はhello-worldでの手順(firewalld + OCIセキュリティリスト双方の開放が必要)を踏襲。**2026-09-20のApache導入に伴い、外部公開ポートは80番・443番のみに変更**(80番はHTTPSへのリダイレクト用、443番は同日のHTTPS化で追加。8080番はfirewalld・OCIセキュリティリストとも削除済み)。OCIセキュリティリストの追加・削除操作自体はユーザーがOCIコンソールで実施する運用。

## デプロイ手順の方針(手動、hello-worldを踏襲)

- VM上の実体: jar配置先 `/opt/clash-royale-api/clash-royale-api.jar`、systemdサービス名 `clash-royale-api.service`(`opc`ユーザーで実行、起動オプション`--spring.profiles.active=prod`)。
- **プレイヤー名の蓄積先(2026-09-18に追加)**: `/var/lib/clash-royale-api/player-index/`(opc所有)。`/etc/clash-royale-api/env`の`PLAYER_INDEX_DIR`で指定している(未指定時はWorkingDirectory配下の`data/player-index`)。jarを差し替えても消えないよう、jar置き場とは分けている。詳細は`プレイヤー名検索（検討中）.md`。
- **索引のバックアップ(2026-09-21に追加)**: ローカルで`powershell -ExecutionPolicy Bypass -File scripts\backup-player-index.ps1`を実行すると、VMの`by-tag`(正本)と`crawler`(巡回の進捗)をtar.gzにして`C:\dev\backup\clash-royale-api\`へ取得する(約170MB、SHA256検証あり、世代3つ)。`by-name`はアプリが起動時に作り直すためバックアップ対象外。**タスクスケジューラで毎週日曜12:00に自動実行される**(タスク名`clash-royale-api-backup`。登録は`scripts\register-backup-task.ps1`、PCが落ちていて逃した回は次に使えるときに取り返す)。成否は`C:\dev\backup\clash-royale-api\backup.log`に1行ずつ残るので、**うまくいっているかはこのログで確認する**。詳細は`進捗ログ.md`のフェーズ1.56・1.57。
- **名前検索用の巡回と整理バッチ(2026-09-19に有効化)**: アプリ内でSpringの定期実行により動く。巡回は`/etc/clash-royale-api/env`の`CRAWLER_ENABLED`・`CRAWLER_INTERVAL`(現在3s。2026-09-20のVM乗り換え時に5sから短縮、2026-09-21に本番の設定値で確認済み)で制御し、止めるときは`CRAWLER_ENABLED=false`にして再起動する。整理バッチ(inbox→by-tag・by-name)は毎時5分(UTC。2026-09-19に1日1回の18:30 UTCから変更)。見かけたプレイヤーが名前検索に出るまで最大2時間ほど。詳細は`進捗ログ.md`のフェーズ1.23。
- **プレイヤー名検索(2026-09-19に有効化)**: `/etc/clash-royale-api/env`の`PLAYER_NAME_SEARCH_ENABLED=true`で有効。やめるときは`false`にして再起動する(入力をすべてタグとして扱う従来の動作に戻る)。索引`by-name/`は整理バッチが作る。
- **APIキーの供給(2026-09-15に変更)**: `/etc/clash-royale-api/env`(root:root 600、`CLASHROYALE_API_TOKEN=...`)をsystemdの`EnvironmentFile`で読み込む。以前あった`/opt/clash-royale-api/application-prod.yml`は`.removed`にリネームして退避済み。jar側にも秘密情報は入っていない。
- **VMがフリーズしやすい点に注意(旧VM時代の経緯)**: 旧VM(E2.1.Micro、1GB)はAlways Free枠の低スペック機のため、デプロイ・再起動の前後でSSH/HTTPともに無応答になることがあった(2026-09-15に2回発生)。数分待って復帰しなければOCIコンソールからの再起動をユーザーに依頼する、という運用だった。
  - 2026-09-19にメモリ不足対策を実施: kdumpを無効化し起動オプションから`crashkernel`を削除(448MBの予約を解放し、使えるメモリが約500MB→945MBに)、`dnf-makecache.timer`を無効化(1回で約330MB使いOOMを起こしていた)。VM全体の再起動からHTTP 200までは約7分かかった。
  - **現行VM(A1.Flex、約5.5GBメモリ)ではこの制約は緩和されている見込みだが、2026-09-20の移行直後のため実運用での再確認はできていない**。フリーズが再発しないか様子見中。再起動後のアプリ起動は40秒前後かかるため、`curl`は数回リトライする前提で確認する点は変わらない。
- SSH鍵: ローカルの `C:\Users\Norio Fukuchi\.ssh\oci_clash_royale_api`。接続先ユーザーは`opc`(Oracle Linux標準)。
- **2026-09-15にロールバック対応のため手順を変更**(`TODO.md`のデプロイ課題対応): 新jarで上書きする前に現行jarをリネームしてバックアップする運用にした。失敗時は`.bak`を戻せば切り戻せる。

1. ローカルで `mvn clean package` してjarをビルド(`target/clash-royale-api-0.0.1-SNAPSHOT.jar`)
2. `scp` でjarをVMの `/tmp/clash-royale-api.jar` に転送
3. VM上で以下を実行してjarを差し替え、サービスを再起動する
   ```bash
   sudo mv /opt/clash-royale-api/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar.bak
   sudo mv /tmp/clash-royale-api.jar /opt/clash-royale-api/clash-royale-api.jar
   sudo chown opc:opc /opt/clash-royale-api/clash-royale-api.jar
   sudo systemctl restart clash-royale-api
   sudo systemctl is-active clash-royale-api
   ```
4. 動作確認は `https://princess-tower.duckdns.org/` へのアクセス(`curl`のHTTPステータス確認でも可)で行う。**2026-09-20のApache導入後は8080番ではなくApache経由(80/443番)でアクセスする**。IP直打ち(`http://161.33.136.175/`)でも到達できる。起動に40秒前後かかるためリトライループで待つこと
5. 問題があれば `.bak` を元のファイル名に戻して`systemctl restart`することでロールバックする(`.bak`は次回デプロイ時に上書きされるため、長期保管はしない)

## コーディング方針(本プロジェクト固有)

- パッケージ構成やController/Service層の分割など、Spring Bootの一般的な作法に従う。過度な抽象化はしない(グローバル方針と同様)。
- **レイヤ構成(2026-09-15のコードレビュー対応で整備)**:
  - `client` … 公式APIとのHTTP通信とDTO。失敗は`client.exception`配下の独自例外に変換して投げる(web層に`RestClientResponseException`や`HttpStatus`を漏らさない)。
  - `domain` … 勝敗判定・集計・役職の序列など、HTTPにもThymeleafにも依存しない計算。
  - `service` … タグ/クラン名の振り分け、ソート、絞り込みなどのアプリケーションロジック。Controllerには置かない。
  - `web` … Controllerは「パラメータを受けてServiceを呼びModelに詰める」だけ。表示用の整形は`ViewMapper`と`web/view`のViewModelで行い、テンプレートにロジックを書かない。
- Clash Royale APIとの通信は専用のクラス(`ClashRoyaleApiClient`)に閉じ込め、Controllerから直接HTTP呼び出しを行わない。
- エラー画面は`GlobalExceptionHandler`(`@ControllerAdvice`)に集約する。Controller内で`model.addAttribute("error", ...)`を書かない。
  - 例外: 画面の一部だけ取得に失敗した場合は、エラー画面にせずその部分に文言を出す。プレイヤー情報画面の対戦履歴(`PlayerController`の`battleLogErrorKey`)と、ランキング(`RankingService`が空リストを返し`ranking.unavailable`を表示)、お気に入り画面(`FavoriteService`が1件ずつ取得できた/見つからない/取得できないの`FavoriteFetch`を返し、行ごとに表示を出し分ける。404は保存してある名前で「見つかりませんでした」を出すのみで自動では外さない)がこれに当たる。
- APIレスポンスはCaffeineで2分キャッシュする(レート制限対策と低スペックVMの負荷軽減)。
- application.properties/yml にAPIキーなどの秘密情報を平文でコミットしない。ローカル用設定と本番用設定は分離する。
- .gitignore には target/, .idea/, *.iml, `/config/`, `application-*.yml` を含める(ファイル名の個別列挙ではなくパターンで弾く)。
  - `config`は必ず先頭に`/`を付けてリポジトリ直下に限定する。`config/`だとソースの`config`パッケージ(`src/main/java/.../config/`)まで無視され、`CacheConfig.java`・`WebConfig.java`が一度もコミットされていなかった(2026-09-17に判明・修正)。
- **日本語を含む`.ps1`はUTF-8 BOM付きで保存する**(2026-09-21に判明)。Windows PowerShell 5.1はBOMが無い`.ps1`をANSI(CP932)として読むため、BOMなしUTF-8だと日本語が文字化けして構文エラーになる。PowerShell 7以降は既定がUTF-8なのでこの問題は起きない。

## セキュリティ上の注意

- Clash Royale APIキー、OCIの認証情報(APIキー、SSH秘密鍵、config)は絶対にリポジトリにコミットしない。
- VMのセキュリティリストは必要最小限のポートのみ開放する(現在はSSH: 22、HTTP: 80、HTTPS: 443の3つだけ。アプリの8080番はApache経由でのみ到達するため外部には開けない)。

## 進捗状況

進捗ログ(何をいつ、なぜやったか、既知の制約・ハマりポイント)は`進捗ログ.md`(プロジェクトルート)を参照。
別セッションで再開する際は、まずそちらを確認する。着手した項目・新たな制約が判明した際は、都度`進捗ログ.md`側を更新する。

## 開発方針

- 大きく作り込まず、機能を1つずつ小さく追加していく方針(2026-09-13にユーザーが表明)。1機能ずつ実装→動作確認→デプロイのサイクルを回す。
- **トップページの個人ランキングは100人表示**(2026-09-20にユーザー判断で確定)。
  - 経緯: 2026-09-19に「トップページはできるだけ軽くする」を大前提の基本方針として表明し10人に絞ったが、2026-09-20にユーザー判断で100人へ変更する方針に転換した。低スペックVM([[project-oci-vm-spec]])向けの軽量化より、トップページで個人ランキングをより多く見せることを優先した形。ページの高さが増える(過去の実測でPC幅5,600px超)ことは承知のうえでの判断。
  - トップページに機能・表示件数をさらに足す判断をするときは、上記の経緯(方針が一度反転している)を踏まえて都度ユーザーに確認する。

## TODO

未着手のタスク一覧は`TODO.md`(プロジェクトルート)を参照。
