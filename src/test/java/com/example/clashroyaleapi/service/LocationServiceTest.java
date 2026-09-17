package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.LocationsResponse;
import com.example.clashroyaleapi.client.exception.ApiUnavailableException;
import com.example.clashroyaleapi.domain.Country;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationServiceTest {

    private ClashRoyaleApiClient apiClient;
    private LocationService locationService;

    @BeforeEach
    void setUp() {
        apiClient = mock(ClashRoyaleApiClient.class);
        locationService = new LocationService(apiClient);
    }

    @Test
    void 大陸やInternationalは国一覧から除外する() {
        when(apiClient.getLocations()).thenReturn(List.of(
                new LocationsResponse.Location(57000006, "International", false, null),
                new LocationsResponse.Location(57000003, "Asia", false, null),
                new LocationsResponse.Location(57000122, "Japan", true, "JP"),
                new LocationsResponse.Location(57000249, "United States", true, "US")));

        List<Country> countries = locationService.countries();

        assertEquals(List.of("JP", "US"), countries.stream().map(Country::countryCode).toList());
        assertEquals("57000122", countries.get(0).locationId());
    }

    @Test
    void 国コードは大文字小文字を問わず引ける() {
        List<Country> countries = List.of(new Country("57000122", "JP", "Japan"));

        assertEquals("57000122", locationService.byCountryCode(countries, "jp").orElseThrow().locationId());
        assertTrue(locationService.byCountryCode(countries, "ZZ").isEmpty());
    }

    @Test
    void API障害時は空リストを返す() {
        when(apiClient.getLocations()).thenThrow(new ApiUnavailableException("boom", null));

        assertTrue(locationService.countries().isEmpty());
    }
}
