package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.LocationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * グローバル/国別タブ(fragments/layout :: rankingTabs)を持つ画面で共通の、対象国の決定とModelへの設定。
 */
@Component
public class RankingScope {

    private final LocationService locationService;
    private final CountryPreference countryPreference;
    private final ViewMapper viewMapper;

    public RankingScope(LocationService locationService, CountryPreference countryPreference, ViewMapper viewMapper) {
        this.locationService = locationService;
        this.countryPreference = countryPreference;
        this.viewMapper = viewMapper;
    }

    public Optional<Country> resolve(String country, HttpServletRequest request, HttpServletResponse response,
            Model model, Locale locale) {
        List<Country> countries = locationService.countries();
        Optional<Country> selected = countryPreference.resolve(country, request, countries);
        boolean requestedExplicitly = country != null && !country.isBlank();
        if (requestedExplicitly) {
            selected.ifPresent(c -> countryPreference.remember(c.countryCode(), response));
        }

        model.addAttribute("countries", viewMapper.toCountryOptions(countries, locale));
        model.addAttribute("selectedCountry", selected.map(Country::countryCode).orElse(null));
        model.addAttribute("selectedCountryName", selected.map(c -> viewMapper.countryName(c, locale)).orElse(null));
        // 国を選び直した直後は、結果が見えるよう国別タブを開いた状態にする。
        model.addAttribute("localTabActive", requestedExplicitly && selected.isPresent());
        return selected;
    }
}
