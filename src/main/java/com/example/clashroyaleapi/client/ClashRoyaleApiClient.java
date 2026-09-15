package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.client.exception.ApiAccessDeniedException;
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

    @Cacheable("players")
    public PlayerResponse getPlayer(String tag) {
        PlayerResponse player = call(() -> restClient.get()
                .uri("/players/{tag}", Tags.normalize(tag))
                .retrieve()
                .body(PlayerResponse.class));
        return requireFound(player, "player " + tag);
    }

    @Cacheable("clans")
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
                .uri(uriBuilder -> uriBuilder.path("/clans").queryParam("name", name).build())
                .retrieve()
                .body(ClanSearchResponse.class));
        return response == null || response.items() == null ? List.of() : response.items();
    }

    // battlelogはAPI仕様上、直近の対戦を返すのみで件数指定やページネーションはできない。返却件数は変動する(上限は保証されない)。
    @Cacheable("battleLogs")
    public List<BattleLogEntry> getBattleLog(String tag) {
        List<BattleLogEntry> log = call(() -> restClient.get()
                .uri("/players/{tag}/battlelog", Tags.normalize(tag))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BattleLogEntry>>() {
                }));
        return log == null ? List.of() : log;
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            // 接続タイムアウト・読み取りタイムアウト・名前解決失敗。未捕捉だと500の白画面に落ちる。
            throw new ApiUnavailableException("clash royale api not reachable", e);
        }
    }

    private ClashRoyaleApiException translate(RestClientResponseException e) {
        return switch (e.getStatusCode().value()) {
            // タグとして不正な形式の場合、公式APIは404ではなく400を返す。どちらも「見つからない」として扱う。
            case 400, 404 -> new ResourceNotFoundException(e.getStatusText(), e);
            case 403 -> new ApiAccessDeniedException(e.getStatusText(), e);
            case 429 -> new ApiRateLimitException(e.getStatusText(), e);
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
