package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.dto.BattleLogEntry;
import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.ClanSearchResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.ClashRoyaleApiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class ClashRoyaleApiClient {

    private final RestClient restClient;

    public ClashRoyaleApiClient(ClashRoyaleApiProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .build();
    }

    public PlayerResponse getPlayer(String tag) {
        return restClient.get()
                .uri("/players/{tag}", normalizeTag(tag))
                .retrieve()
                .body(PlayerResponse.class);
    }

    public ClanResponse getClan(String tag) {
        return restClient.get()
                .uri("/clans/{tag}", normalizeTag(tag))
                .retrieve()
                .body(ClanResponse.class);
    }

    // 公式APIのクラン名検索。タグ検索と異なり、完全一致ではなく部分一致で検索される。
    public List<ClanSearchResponse.ClanSummary> searchClansByName(String name) {
        ClanSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/clans").queryParam("name", name).build())
                .retrieve()
                .body(ClanSearchResponse.class);
        return response != null ? response.items() : List.of();
    }

    // battlelogはAPI仕様上、直近の対戦(実質25件程度)を返すのみで件数指定やページネーションはできない。
    public List<BattleLogEntry> getBattleLog(String tag) {
        return restClient.get()
                .uri("/players/{tag}/battlelog", normalizeTag(tag))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BattleLogEntry>>() {
                });
    }

    // 先頭に "#" を補う。"#" のURLエンコード(%23)自体はRestClientのURIビルダーがパス変数展開時に自動で行う。
    private String normalizeTag(String tag) {
        return tag.startsWith("#") ? tag : "#" + tag;
    }
}
