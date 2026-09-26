package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.dto.ClanRankingResponse;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.LocationsResponse;
import com.example.clashroyaleapi.client.dto.PlayerRankingResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.dto.RiverRaceLogResponse;
import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
import com.example.clashroyaleapi.client.exception.ApiMaintenanceException;
import com.example.clashroyaleapi.client.exception.ApiRateLimitException;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.client.exception.ResourceNotFoundException;
import com.example.clashroyaleapi.config.ClashRoyaleApiProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.function.Supplier;

@Component
public class ClashRoyaleApiClient {

    private final RestClient restClient;

    /**
     * RestClient.Builder はSpring Bootが用意したものを受け取る。自前で RestClient.builder() を呼ぶと
     * spring.http.client.* のタイムアウト設定やHTTPクライアントのメトリクスが一切効かなくなるため。
     */
    public ClashRoyaleApiClient(RestClient.Builder builder, ClashRoyaleApiProperties properties) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    // キーは正規化後のタグにする。"2pyl" と "#2PYL" のような表記違いで同じプレイヤーを取り直さないため。
    @Cacheable(cacheNames = "players", key = "T(com.example.clashroyaleapi.client.Tags).normalize(#tag)")
    public PlayerResponse getPlayer(String tag) {
        PlayerResponse player = call(() -> restClient.get()
                .uri("/players/{tag}", Tags.normalize(tag))
                .retrieve()
                .body(PlayerResponse.class));
        return requireFound(player, "player " + tag);
    }

    @Cacheable(cacheNames = "clans", key = "T(com.example.clashroyaleapi.client.Tags).normalize(#tag)")
    public ClanResponse getClan(String tag) {
        ClanResponse clan = call(() -> restClient.get()
                .uri("/clans/{tag}", Tags.normalize(tag))
                .retrieve()
                .body(ClanResponse.class));
        return requireFound(clan, "clan " + tag);
    }

    // 公式APIのクラン名検索。タグ検索と異なり、完全一致ではなく部分一致で検索される。
    @Cacheable("clanSearches")
    public List<ClanSearchResponse.ClanSummary> searchClansByName(String name) {
        ClanSearchResponse response = call(() -> restClient.get()
                // 名前はURI変数として渡す。queryParam に直接入れると "{" がURI変数として展開されて例外になり、
                // "+" もエンコードされず公式API側で空白と解釈される。
                .uri(uriBuilder -> uriBuilder.path("/clans").queryParam("name", "{name}").build(name))
                .retrieve()
                .body(ClanSearchResponse.class));
        return response == null || response.items() == null ? List.of() : response.items();
    }

    /**
     * 指定範囲のクランランキング上位。locationId には "global" か /locations が返す数値IDを渡す。
     * プレイヤーのトロフィーランキング(/rankings/players)は現在の公式API仕様では常に空配列を返すため使わない。
     */
    @Cacheable("clanRankings")
    public List<ClanRankingResponse.RankedClan> getClanRankings(String locationId, int limit) {
        ClanRankingResponse response = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/locations/{locationId}/rankings/clans")
                        .queryParam("limit", limit)
                        .build(locationId))
                .retrieve()
                .body(ClanRankingResponse.class));
        return response == null || response.items() == null ? List.of() : response.items();
    }

    /**
     * カード一覧(通常のカードとタワーユニット)。カードの詳細表示に使う。
     * 公式APIは説明文やステータスを返さないため、名前・画像・レアリティ・エリクサー・最大レベル・進化の有無だけが取れる。
     */
    @Cacheable("cards")
    public CardsResponse getCards() {
        CardsResponse response = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cards").build())
                .retrieve()
                .body(CardsResponse.class));
        if (response == null) {
            return new CardsResponse(List.of(), List.of());
        }
        return new CardsResponse(
                response.items() == null ? List.of() : response.items(),
                response.supportItems() == null ? List.of() : response.supportItems());
    }

    /**
     * 国・地域の一覧。公式APIのlocationIdは国コードから機械的に導けないため、この一覧から引く必要がある。
     * 内容はほぼ変化しないので、キャッシュが切れてもAPIへの負荷は小さい。
     */
    @Cacheable("locations")
    public List<LocationsResponse.Location> getLocations() {
        LocationsResponse response = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/locations").queryParam("limit", 1000).build())
                .retrieve()
                .body(LocationsResponse.class));
        return response == null || response.items() == null ? List.of() : response.items();
    }

    /**
     * パス・オブ・レジェンドの個人ランキング上位(現在シーズン)。locationIdは "global" か数値ID。
     * トロフィーの個人ランキング(/rankings/players)は公式API側が常に空配列を返すため、こちらを使う。
     * なお /pathoflegend/{seasonId}/rankings/players は確定済みシーズン専用で、当月や国別は404になる。
     */
    @Cacheable("playerRankings")
    public List<PlayerRankingResponse.RankedPlayer> getPathOfLegendRankings(String locationId, int limit) {
        PlayerRankingResponse response = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/locations/{locationId}/pathoflegend/players")
                        .queryParam("limit", limit)
                        .build(locationId))
                .retrieve()
                .body(PlayerRankingResponse.class));
        return response == null || response.items() == null ? List.of() : response.items();
    }

    // battlelogはAPI仕様上、直近の対戦を返すのみで件数指定やページネーションはできない。返却件数は変動する(上限は保証されない)。
    @Cacheable(cacheNames = "battleLogs", key = "T(com.example.clashroyaleapi.client.Tags).normalize(#tag)")
    public List<BattleLogEntry> getBattleLog(String tag) {
        return fetchBattleLog(tag);
    }

    /** 巡回用。getRiverRaceLog と同じ理由でキャッシュしない。 */
    public List<BattleLogEntry> getBattleLogUncached(String tag) {
        return fetchBattleLog(tag);
    }

    private List<BattleLogEntry> fetchBattleLog(String tag) {
        List<BattleLogEntry> log = call(() -> restClient.get()
                .uri("/players/{tag}/battlelog", Tags.normalize(tag))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BattleLogEntry>>() {
                }));
        return log == null ? List.of() : log;
    }

    /**
     * クラン対戦の履歴。巡回専用なので、意図的に @Cacheable を付けていない。
     * 巡回の結果をキャッシュに載せると、2度は使わないデータでメモリが埋まり、利用者の分が追い出されるため。
     */
    public RiverRaceLogResponse getRiverRaceLog(String clanTag, int limit) {
        RiverRaceLogResponse response = call(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/clans/{tag}/riverracelog")
                        .queryParam("limit", limit)
                        .build(Tags.normalize(clanTag)))
                .retrieve()
                .body(RiverRaceLogResponse.class));
        return response == null || response.items() == null ? new RiverRaceLogResponse(List.of()) : response;
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            // 接続タイムアウト・読み取りタイムアウト・名前解決失敗。未捕捉だと500の白画面に落ちる。
            throw new ApiUnavailableException("clash royale api not reachable", e);
        } catch (RestClientException e) {
            // 本文の読み取り中に接続が切れた(EOF)場合などは、ResourceAccessExceptionではなくこの例外で届く。
            throw new ApiUnavailableException("failed to read clash royale api response", e);
        }
    }

    static ClashRoyaleApiException translate(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            // タグとして不正な形式の場合、公式APIは404ではなく400を返す。どちらも「見つからない」として扱う。
            case 400, 404 -> new ResourceNotFoundException(e.getStatusText(), e);
            case 403 -> new ApiAccessDeniedException(e.getStatusText(), e);
            case 429 -> new ApiRateLimitException(e.getStatusText(), e);
            // メンテナンス中は {"reason":"inMaintenance",...} が返る(2026-09-24に本番ログで確認)。
            case 503 -> e.getResponseBodyAsString().contains("\"inMaintenance\"")
                    ? new ApiMaintenanceException("in maintenance", e)
                    : new ApiUnavailableException(e.getStatusCode() + " " + e.getStatusText(), e);
            default -> new ApiUnavailableException(e.getStatusCode() + " " + e.getStatusText(), e);
        };
    }

    // RestClient は 204 やボディ空のときに null を返しうるため、呼び出し側でNPEにせず例外に寄せる。
    private <T> T requireFound(T body, String what) {
        if (body == null) {
            throw new ResourceNotFoundException("empty body for " + what, null);
        }
        return body;
    }
}
