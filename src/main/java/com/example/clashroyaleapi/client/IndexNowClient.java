package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * IndexNow(https://www.indexnow.org/)に、新しくできたページのURLを送る。1か所に送れば、対応している検索エンジン
 * (Bing・Naver・Yandexなど)の間で共有される。
 */
@Component
public class IndexNowClient {

    private static final String ENDPOINT = "https://api.indexnow.org/indexnow";

    /** 1回で送れるURLの上限(IndexNowの決まり)。 */
    public static final int MAX_URLS = 10_000;

    private final RestClient restClient;

    public IndexNowClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * 受け付けられれば(200・202)何も返さない。鍵が確かめられない(403)・URLが別のホスト(422)などの4xxや、
     * 通信の失敗・5xxは例外にする(送ったことにしないため)。
     */
    public void submit(String host, String key, String keyLocation, List<String> urls) {
        try {
            restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("host", host, "key", key, "keyLocation", keyLocation, "urlList", urls))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new ApiUnavailableException("indexnow submission failed: " + e.getMessage(), e);
        }
    }
}
