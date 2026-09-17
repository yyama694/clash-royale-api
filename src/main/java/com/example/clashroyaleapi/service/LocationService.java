package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.LocationsResponse;
import com.example.clashroyaleapi.client.exception.ClashRoyaleApiException;
import com.example.clashroyaleapi.domain.Country;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final ClashRoyaleApiClient apiClient;

    public LocationService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * ランキングの対象にできる国・地域。
     * 公式APIのlocationsには大陸や "International" も含まれるため、国だけに絞る。
     */
    public List<Country> countries() {
        try {
            return apiClient.getLocations().stream()
                    .filter(location -> location.isCountry() && location.countryCode() != null
                            && !location.countryCode().isBlank())
                    .map(location -> new Country(String.valueOf(location.id()), location.countryCode(),
                            location.name()))
                    .toList();
        } catch (ClashRoyaleApiException e) {
            // 国一覧が取れないだけでトップページ全体を落とさない(国別ランキングは非表示になる)。
            log.warn("locations unavailable: {}", e.toString());
            return List.of();
        }
    }

    public Optional<Country> byCountryCode(List<Country> countries, String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = countryCode.strip().toUpperCase(Locale.ROOT);
        return countries.stream().filter(country -> country.countryCode().equals(normalized)).findFirst();
    }
}
