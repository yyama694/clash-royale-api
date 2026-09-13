# プロジェクト設定: Clash Royale API + Spring Boot + OCI デプロイ

このファイルは本プロジェクト(Clash Royale公式APIを使ったWebアプリのOCIデプロイ)専用の作業方針です。
共通の作業方針はグローバルのCLAUDE.mdを参照。個別ルールが競合する場合は本ファイルを優先する。
基本構成・進め方は `C:\dev\hello-world` プロジェクトを踏襲しているが、下記の点は本プロジェクト用に変更している。

## 進め方(hello-worldからの変更点)

- ユーザーはhello-worldプロジェクトでSpring Boot + OCIデプロイを一通り経験済みのため、hello-worldで採用した「1作業ごとに事前説明+確認」という細かい進め方は本プロジェクトでは採用しない。
- 通常セッションと同等の粒度で進めてよい。ただし下記のような大きな判断・不可逆な操作は引き続き事前確認する(グローバル方針・セッション方針と同様)。
  - OCIインフラの作成・削除
  - git push、GitHubリポジトリ作成
  - 課金が発生し得る操作
  - 既存データ・設定の削除や上書き
- 専門用語が出た際に簡単な補足を添える方針(グローバルCLAUDE.md)は本プロジェクトでも引き続き適用する。
- 確認は自由入力ではなく選択肢形式(AskUserQuestion)で行う(hello-worldでの決定を踏襲)。
- **例外**: `CLAUDE.md`など`.md`ファイルへの進捗状況の記入・更新は、事前確認なしで行ってよい(2026-09-13にユーザーから明示指示。hello-worldでの運用を踏襲)。実際のコード変更・コマンド実行・git操作・外部サービスへの操作(OCI操作、GitHubへのpushなど)は引き続き大きな判断のみ事前確認する。

## プロジェクト概要

- 目的: Clash Royale公式API(developer.clashroyale.com)を利用し、Clash Royaleユーザー向けにプレイヤー情報・クラン情報などを閲覧できるWebアプリを提供する。学習(Spring Boot実践)と実用を兼ねる。
- 最初のマイルストーンは最小機能(プレイヤー検索・クラン検索)とし、その後クイズ機能など追加サービスを継続的に足していく方針(2026-09-13時点でユーザーが表明)。
- ソース管理: GitHub(このディレクトリでgit初期化 → GitHubリポジトリにpush、hello-worldと同様の手順)。
- デプロイは当面手動(SSH接続してjarを配置・再起動)。CI/CD自動化は将来検討。

## 提供機能(段階的に追加)

- **フェーズ1(最小機能)**: プレイヤータグ検索(実績・カードレベル・戦績表示)、クランタグ検索(メンバー一覧・トロフィー・戦争実績表示)。
- **フェーズ2以降(未着手・アイデア段階)**: クラロワクイズなど、ユーザーが継続的に遊べる追加コンテンツ。着手時にこのセクションを更新する。

## 技術スタック

- 言語/フレームワーク: Java 21 (LTS) + Spring Boot
- ビルドツール: Maven
- 画面: Thymeleaf(サーバーサイドレンダリング。hello-worldと同じ方針)
- DB: **当面なし**。まずはClash Royale APIの呼び出し結果をそのまま画面に表示する構成で開始し、キャッシュやクイズデータの保存が必要になった段階でPostgreSQL導入を検討する(2026-09-13時点でユーザーが決定)。
- Webサーバー: Spring Boot組み込みTomcatを直接公開(hello-worldと同様、当面リバースプロキシなし)。

## Clash Royale API連携に関する注意点

- APIキーは2026-09-13時点で**未取得**。developer.clashroyale.comでの登録・キー発行をこれから一緒に行う。
- **重要な制約**: Clash Royale公式APIのキーは、アクセス元のIPアドレスを事前に登録(ホワイトリスト)する方式。
  - 本番(OCI VM)用と、ローカル開発用とでIPアドレスが異なる点に注意。
  - ローカル開発機のグローバルIPは固定でない可能性があるため、開発中にAPIが403エラーになった場合はIPアドレスが変わっていないか、developer.clashroyale.com側のキー設定を確認する。
  - OCI VMのパブリックIPが確定した時点で、そのIPも忘れずにキーの許可リストに追加する。
- APIキー(トークン)は絶対にリポジトリにコミットしない。環境変数、またはgitignore対象の設定ファイル(`application-local.yml`等)で管理する。
- Clash Royale APIには呼び出し回数制限がある可能性があるため、実装時に公式ドキュメントでレート制限を確認する。

## インフラ構成(OCI)

- hello-worldプロジェクトで作成したOCI VM(VM.Standard.E2.1.Micro、`140.245.83.216`)は2026-09-13時点でユーザーの意向により削除し、本プロジェクト用に新規VMを作り直す方針(ローカルの`C:\dev\hello-world`フォルダ・GitHubリポジトリ自体は学習記録としてそのまま残す)。
- 新規VMのスペック・リージョン等はhello-worldでの実績(Always Free枠、東京リージョン、"Out of host capacity"でARM(Ampere)が作れずx86_64のVM.Standard.E2.1.Microで作成、という経緯)を踏まえて構築時に決定する。詳細は構築が進み次第このセクションに追記する。
- ネットワーク公開・ポート開放・ファイアウォール設定はhello-worldでの手順(firewalld + OCIセキュリティリスト双方の開放が必要)を踏襲する。

## デプロイ手順の方針(手動、hello-worldを踏襲)

1. ローカルで `mvn clean package` してjarをビルド
2. `scp` 等でjarをOCI VMの所定ディレクトリに転送
3. VM上でsystemdサービスを再起動してアプリを反映
4. 動作確認は `http://<パブリックIP>:8080` へのアクセスで行う

## コーディング方針(本プロジェクト固有)

- パッケージ構成やController/Service層の分割など、Spring Bootの一般的な作法に従う。過度な抽象化はしない(グローバル方針と同様)。
- Clash Royale APIとの通信は専用のServiceクラス(例: `ClashRoyaleApiClient`)に閉じ込め、Controllerから直接HTTP呼び出しを行わない。
- application.properties/yml にAPIキーなどの秘密情報を平文でコミットしない。ローカル用設定と本番用設定は分離する。
- .gitignore には target/, .idea/, *.iml, application-local.yml(または同等の秘匿設定ファイル)を含める。

## セキュリティ上の注意

- Clash Royale APIキー、OCIの認証情報(APIキー、SSH秘密鍵、config)は絶対にリポジトリにコミットしない。
- VMのセキュリティリストは必要最小限のポートのみ開放する(SSH: 22、アプリ: 8080など)。

## 進捗状況(セッションをまたぐための記録)

Claude Codeはセッションをまたいだ記憶を持たないため、作業のキリが良いタイミングでこのセクションを更新し、
「どこまで完了したか」「次に何をするか」を常に最新の状態に保つ。別セッションで再開する際は、まずこのセクションを確認する。

- [x] CLAUDE.md作成、方針決定(機能範囲・DB方針・インフラ方針・進め方など)
- [x] Clash Royale公式APIへの登録・APIキー取得
  - developer.clashroyale.comでSupercell IDログイン → APIキー発行済み
  - Allowed IP Addressesにはローカル開発マシンのグローバルIPを登録済み。**OCI VM作成後、そのVMのグローバルIPも追加登録する必要あり(未実施)**
  - キー本体は `src/main/resources/application-local.yml`(gitignore対象、コミットされない)に保存済み
- [x] Spring Bootプロジェクトの雛形作成(pom.xml、起動クラス、Controller、Thymeleafテンプレート)
  - groupId=`com.example`, artifactId=`clash-royale-api`, Java 21 / Spring Boot 3.3.4 / Web + Thymeleaf
  - `ClashRoyaleApiClient`(RestClient使用、Bearer認証、タグの`#`はURIビルダーが自動エンコード)
  - 設定は`application.yml`(コミット対象、tokenは空)+`application-local.yml`(gitignore対象、実トークン)の2層構成。profiles.active=localをデフォルトに設定
- [x] プレイヤー検索機能の実装(`/player?tag=...`、`PlayerController`+`player.html`)
- [x] クラン検索機能の実装(`/clan?tag=...`、`ClanController`+`clan.html`)
- [x] ローカル動作確認
  - `mvn clean package`でビルド成功、`java -jar`で起動確認
  - 実際のAPIキーで動作確認: プレイヤー`#VRQUQ0QL`(yyama8888)、クラン`#PPCQ9R2R`(償い、メンバー49名)とも正常表示
  - 存在しないタグでの404エラーハンドリングも確認済み(画面にエラーメッセージ表示)
  - 既知の注意点: クランメンバーの`expLevel`は実際のAPIレスポンス自体が0を返す(アプリ側のバグではなく元データの仕様)
- [x] git初期化 + GitHubリポジトリ作成・連携
  - このリポジトリのみに`user.name`/`user.email`をローカル設定(グローバルgit設定は変更せず、hello-worldと同じ方式)
  - `gh repo create clash-royale-api --public --source=. --remote=origin --push`でリポジトリ作成・push完了
  - リポジトリURL: https://github.com/yyama694/clash-royale-api (Public)
- [x] 旧hello-world用OCI VM(140.245.83.216)を削除(ユーザーがOCIコンソールから実施)
- [x] 新規OCI VM作成
  - リージョン: 東京(ap-tokyo-1)
  - Shape: 当初Ampere A1.Flexを試みたが"Out of host capacity"のため**VM.Standard.E2.1.Micro**(x86_64)で作成(hello-worldと同じ経緯)
  - OS: Oracle Linux Server 9.8
  - SSH鍵: `C:\Users\Norio Fukuchi\.ssh\oci_clash_royale_api`(hello-world用鍵ファイルをリネームして流用)
  - **パブリックIPアドレス: `132.226.7.203`**
  - SSH接続確認済み(`ssh -i oci_clash_royale_api opc@132.226.7.203`)
- [x] Clash Royale APIキーの許可IPリストに、このVMのパブリックIP(`132.226.7.203`)を追加登録
  - **既知の注意点**: developer.clashroyale.comでは既存キーのAllowed IP Addressesは編集不可(追加不可)。IPを追加したい場合はキーを作り直す必要がある。
  - 経緯: 誤って複数キーを作成してしまい混乱したため、最終的にキーを1本(`clash-royale-api-local`)に整理し、作成時にAllowed IP Addressesへ`60.113.8.212`(ローカル開発機)と`132.226.7.203`(OCI VM)の両方を登録した
  - **既知の注意点**: キー作成/IP変更後、APIに反映されるまで最大で1分弱のタイムラグがあり、その間は`403 Forbidden`(`{"reason":"accessDenied","message":"Invalid authorization"}`)になる。時間を置いて再試行すれば解決する。
  - `application-local.yml`のトークンを更新し、ローカルで動作確認済み(`#VRQUQ0QL`のプレイヤー情報取得に成功)
- [x] VM上にJava 21実行環境をセットアップ
  - Amazon Corretto 21のtar.gz(x64版)を`/opt/amazon-corretto-21.0.12.9.1-linux-x64`に展開(hello-worldと同じ、dnf経由は避ける方針)
  - `/etc/profile.d/java21.sh`でJAVA_HOME/PATHを設定
- [x] jarをVMに転送し、systemdサービスとして常駐化
  - ローカルで`mvn clean package -DskipTests`でビルド → `scp`で`/opt/clash-royale-api/clash-royale-api.jar`に転送
  - 本番用APIキー設定は`/opt/clash-royale-api/application-prod.yml`(chmod 600、Spring Bootの外部設定ファイル読み込みの仕組みでjarと同じディレクトリに置くだけで`--spring.profiles.active=prod`指定時に自動適用される)
  - `/etc/systemd/system/clash-royale-api.service`を作成(`Type=simple`、`User=opc`、`ExecStart`はCorretto21の`java -jar ... --spring.profiles.active=prod`)
  - **既知の注意点(ハマりポイント)**: `scp`で`/tmp`経由でサービスファイルを配置したところ、SELinux(Enforcing)のコンテキストが`user_tmp_t`のままになり`systemctl enable`が「Unit file does not exist」というわかりにくいエラーで失敗した。`sudo restorecon -v /etc/systemd/system/clash-royale-api.service`でコンテキストを`systemd_unit_file_t`に修復して解決。今後`/tmp`経由で設定ファイルを配置する際は同様の問題に注意する。
  - `systemctl daemon-reload` → `enable --now`で起動、`active (running)`を確認
  - VM内部の`curl http://localhost:8080/player?tag=...`で実際のClash Royale APIを使った動作確認済み(profile: prod)
- [x] VM内部のfirewalldで8080番ポートを開放(`firewall-cmd --add-port=8080/tcp --permanent && --reload`)
- [x] OCIコンソールでセキュリティリストにIngress Rule追加(ユーザーが実施)
  - VCN `vcn-20260913-1841` の `Default Security List` に、Source CIDR `0.0.0.0/0` / TCP / ポート8080のIngress Ruleを追加
- [x] 外部からのアクセス確認 → `curl http://132.226.7.203:8080/` でHTTP 200、プレイヤー検索(`/player?tag=%23VRQUQ0QL`)も正常動作を確認。**外部アクセス成功**

## マイルストーン完了

プレイヤー検索・クラン検索の最小機能を持つClash Royale APIのWebアプリが、OCI上へのデプロイまで完了した。
ブラウザから `http://132.226.7.203:8080/` にアクセスすると、プレイヤータグ・クランタグで検索できる。

- [x] プレイヤー名/クランメンバー名からタグを検索する機能を追加(2026-09-13実装・VMへデプロイ・外部からの動作確認済み)
  - **技術的制約**: Clash Royale公式APIに名前検索エンドポイントがないため、これまでにプレイヤー検索・クラン検索で判明した「名前→タグ」の対応をテキストファイル(JSON)に蓄積し、その中から部分一致で検索する方式を採用(2026-09-13にユーザーと合意。利用者が少ないためDBではなくテキストファイルで十分と判断)。
  - `NameIndexService`(`src/main/java/.../nameindex/`)が`/player`・`/clan`検索成功時に名前・タグを自動登録し、`data/name-index.json`(gitignore対象、実行時生成)に永続化。
  - `/search?name=...`(`SearchController`)で部分一致検索、結果からプレイヤー/クランとして見るリンクへ遷移可能。トップページにも検索フォームを追加。
  - **既知の制約**: まだ一度も検索されたことのない名前はヒットしない(蓄積型のため)。デプロイ後はVM上の`data/`ディレクトリ(jarと同じ作業ディレクトリ配下)にファイルが作られる想定。既存のVM上jarを更新する際、`data/name-index.json`を誤って消さないよう注意。
- [x] プレイヤータグ検索に直近の対戦履歴表示を追加(2026-09-13実装・VMへデプロイ・外部からの動作確認済み)
  - `GET /players/{tag}/battlelog`を`ClashRoyaleApiClient.getBattleLog()`で呼び出し、`/player`ページに対戦履歴テーブルを表示。
  - **既知の制約**: ユーザーからは「直近50試合」の要望があったが、公式APIのbattlelogは件数指定・ページネーション非対応で、直近の対戦(実質25件程度)しか返せない仕様のため、その旨を画面にも注記した上でAPIが返す件数をそのまま表示している。

次のマイルストーン(クイズ機能などの追加サービス、DB導入)は未着手。

- [x] 上記2機能(名前検索・対戦履歴表示)をVMへデプロイ(2026-09-13)
  - デプロイ手順は既存方針どおり(`mvn clean package -DskipTests` → `scp`で`clash-royale-api.jar`を上書き → `sudo systemctl restart clash-royale-api`)。`sudo`はパスワードなしで実行可能(opcユーザーにNOPASSWD設定済みと判明)。
  - **注意点(2026-09-13発生)**: デプロイ作業中、VMがSSH(22)・HTTP(8080)・ping全てに無応答になる事象が発生。ユーザーがOCIコンソールからVMをリブートして復旧した。原因は未特定(VM自体のフリーズ等の可能性。[[project-oci-vm-spec]]の通りVM.Standard.E2.1.Microは低スペックのため要注意)。リブート後もsshdの起動やSpring Boot自体の起動(データ量にもよるが30〜75秒程度)に時間がかかるため、疎通確認は焦らず数分単位の間隔でリトライするとよい。
  - デプロイ後、外部(`http://132.226.7.203:8080/`)からトップページ・プレイヤー検索(対戦履歴表示含む)・名前検索(`/search`)の動作を確認済み。`data/name-index.json`もVM上に生成され、正しく蓄積されることを確認済み。

## 未確定・今後検討する事項

- **開発方針**: 大きく作り込まず、機能を1つずつ小さく追加していく方針(2026-09-13にユーザーが表明)。1機能ずつ実装→動作確認→デプロイのサイクルを回す。
- クイズ機能など追加サービスの詳細仕様
- DB導入のタイミングと方式(名前検索は当面テキストファイル方式で運用。利用者が増えるなどして必要になった時点でPostgreSQL移行を検討)
- ドメイン取得・HTTPS化
- CI/CD自動化
