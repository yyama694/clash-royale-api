package com.example.clashroyaleapi.web;

import com.example.clashroyaleapi.domain.Country;
import com.example.clashroyaleapi.service.LocationService;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RankingScopeTest {

    private static final List<Country> COUNTRIES = List.of(
            new Country("57000122", "JP", "Japan"),
            new Country("57000249", "US", "United States"));

    private LocationService locationService;
    private RankingScope rankingScope;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ExtendedModelMap model;

    @BeforeEach
    void setUp() {
        locationService = mock(LocationService.class);
        ViewMapper viewMapper = mock(ViewMapper.class);
        when(viewMapper.toCountryOptions(any(), any())).thenReturn(List.of());
        when(viewMapper.countryName(any(), any())).thenAnswer(invocation -> invocation.<Country>getArgument(0).englishName());
        rankingScope = new RankingScope(locationService, new CountryPreference(), viewMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        model = new ExtendedModelMap();
    }

    @Test
    void 国を明示的に選んだときはCookieに保存し国別タブを開く() {
        when(locationService.countries()).thenReturn(COUNTRIES);

        Optional<Country> selected = rankingScope.resolve("us", request, response, model, Locale.ENGLISH);

        assertEquals("US", selected.orElseThrow().countryCode());
        assertEquals("US", response.getCookie(WebConstants.COUNTRY_PARAM).getValue());
        assertEquals("US", model.get("selectedCountry"));
        assertEquals("United States", model.get("selectedCountryName"));
        assertEquals(true, model.get("localTabActive"));
    }

    @Test
    void 国を指定しないときはCookieを書き換えずグローバルタブのまま() {
        when(locationService.countries()).thenReturn(COUNTRIES);
        request.setCookies(new Cookie(WebConstants.COUNTRY_PARAM, "US"));

        Optional<Country> selected = rankingScope.resolve(null, request, response, model, Locale.ENGLISH);

        assertEquals("US", selected.orElseThrow().countryCode());
        assertNull(response.getCookie(WebConstants.COUNTRY_PARAM));
        assertEquals(false, model.get("localTabActive"));
    }

    @Test
    void 存在しない国を指定されたときは保存せず前回の選択にフォールバックする() {
        when(locationService.countries()).thenReturn(COUNTRIES);
        request.setCookies(new Cookie(WebConstants.COUNTRY_PARAM, "US"));

        Optional<Country> selected = rankingScope.resolve("ZZ", request, response, model, Locale.ENGLISH);

        assertEquals("US", selected.orElseThrow().countryCode());
        assertNull(response.getCookie(WebConstants.COUNTRY_PARAM));
        // 選び直しが成立していないので、国別タブを開いて「選んだ国の結果」に見せない。
        assertEquals(false, model.get("localTabActive"));
    }

    @Test
    void 国一覧が取れないときは国別タブを出さない() {
        when(locationService.countries()).thenReturn(List.of());

        Optional<Country> selected = rankingScope.resolve("JP", request, response, model, Locale.JAPANESE);

        assertTrue(selected.isEmpty());
        assertNull(model.get("selectedCountry"));
        assertNull(model.get("selectedCountryName"));
        assertEquals(false, model.get("localTabActive"));
        assertNull(response.getCookie(WebConstants.COUNTRY_PARAM));
    }
}
