package com.example.clashroyaleapi.client;

import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/**
 * カード画像の置き場所(api-assets.clashroyale.com)に、画像が本当にあるかを確かめる。
 * 公式APIは、置き場所にまだ無い進化・ヒーローの画像のURLを返すことがある(CardImageService)。
 */
@Component
public class CardImageClient {

    private final RestClient restClient;

    public CardImageClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /** 4xxなら無い。通信の失敗や5xxでは、あるかどうか分からないので例外にする。 */
    public boolean exists(String url) {
        try {
            restClient.head().uri(URI.create(url)).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            return false;
        } catch (RestClientException e) {
            throw new ApiUnavailableException("card image host not reachable: " + url, e);
        }
    }
}
