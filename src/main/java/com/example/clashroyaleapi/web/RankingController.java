package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.LocationService;
import com.example.clashroyaleapi.service.RankingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
public class RankingController {

    private final RankingService rankingService;
    private final LocationService locationService;
    private final CountryPreference countryPreference;
    private final ViewMapper viewMapper;

    public RankingController(RankingService rankingService, LocationService locationService,
            CountryPreference countryPreference, ViewMapper viewMapper) {
        this.rankingService = rankingService;
        this.locationService = locationService;
        this.countryPreference = countryPreference;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/ranking")
    public String ranking(@RequestParam(required = false) String country, HttpServletRequest request,
            HttpServletResponse response, Model model, Locale locale) {
        List<Country> countries = locationService.countries();
        Optional<Country> selected = countryPreference.resolve(country, request, countries);
        boolean requestedExplicitly = country != null && !country.isBlank();
        if (requestedExplicitly) {
            selected.ifPresent(c -> countryPreference.remember(c.countryCode(), response));
        }

        // タブ切り替えはブラウザ側で行うため、両方のランキングをここで取得しておく(切り替え時に再通信しない)。
        model.addAttribute("globalRanking",
                viewMapper.toClanRankingRows(rankingService.topClans(RankingService.GLOBAL_LOCATION_ID), locale));
        model.addAttribute("localRanking", selected
                .map(c -> viewMapper.toClanRankingRows(rankingService.topClans(c.locationId()), locale))
                .orElseGet(List::of));
        model.addAttribute("countries", viewMapper.toCountryOptions(countries, locale));
        model.addAttribute("selectedCountry", selected.map(Country::countryCode).orElse(null));
        model.addAttribute("selectedCountryName", selected.map(c -> viewMapper.countryName(c, locale)).orElse(null));
        // 国を選び直した直後は、結果が見えるよう国別タブを開いた状態にする。
        model.addAttribute("localTabActive", requestedExplicitly && selected.isPresent());
        return "ranking";
    }
}
