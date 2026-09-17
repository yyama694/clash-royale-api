package com.example.clashroyaleapi.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationsResponse(List<Location> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(int id, String name, boolean isCountry, String countryCode) {
    }
}
