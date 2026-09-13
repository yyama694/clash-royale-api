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
- [ ] git初期化 + GitHubリポジトリ作成・連携
- [ ] 新規OCI VM作成(旧hello-world用VMは削除)
- [ ] VM上に実行環境構築、デプロイ、外部アクセス確認

## 未確定・今後検討する事項

- クイズ機能など追加サービスの詳細仕様
- DB導入のタイミングと方式(必要になった時点で検討)
- ドメイン取得・HTTPS化
- CI/CD自動化
