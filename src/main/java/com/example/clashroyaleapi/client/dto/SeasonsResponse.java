package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 終わったシーズンの一覧(古い順)。id は "2026-09" の形で、同じ id が続けて入っていることがある。
 * 進行中のシーズンは入らない(2026-10-05のシーズン切り替え直後に、末尾が "2026-09" だった)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonsResponse(List<Season> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(String id) {
    }
}
