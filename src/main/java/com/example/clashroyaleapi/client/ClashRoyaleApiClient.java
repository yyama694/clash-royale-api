package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.dto.ClanResponse;
import com.example.clashroyaleapi.client.dto.PlayerResponse;
import com.example.clashroyaleapi.config.ClashRoyaleApiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    // 先頭に "#" を補う。"#" のURLエンコード(%23)自体はRestClientのURIビルダーがパス変数展開時に自動で行う。
    private String normalizeTag(String tag) {
        return tag.startsWith("#") ? tag : "#" + tag;
    }
}
