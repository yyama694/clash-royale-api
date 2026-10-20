package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.BalanceUpdate;
import com.example.clashroyaleapi.domain.BalanceUsage;
import com.example.clashroyaleapi.domain.CardForm;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.TopDecks;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalanceServiceTest {

    private static CardUsage usage(String time, List<Integer> deck) {
        return CardUsage.of(new TopDecks(Instant.parse(time), List.of(
                new TopDecks.SampledDeck(deck, null), new TopDecks.SampledDeck(List.of(9), null))));
    }

    @Test
    void 後の調整で同じカードの別の形が変わっても次の調整の前の集計で止める() {
        List<BalanceUpdate> updates = BalanceUpdate.parse(List.of(
                "update\t2026-10-06\thttps://example.com/1",
                "change\tSkeleton Barrel\tnormal\tnerf\tdamage\t1\t2\t3",
                "change\tKnight\tnormal\tbuff\tdamage\t1\t2\t3",
                "update\t2026-10-19\thttps://example.com/2",
                "change\tSkeleton Barrel\tevolution\trework\tdeathDamage\t192\t163\t-15"));
        CardUsageService cardUsageService = mock(CardUsageService.class);
        CardService cardService = mock(CardService.class);
        when(cardService.cardsByName()).thenReturn(Map.of(
                "Skeleton Barrel", new CardsResponse.Card(1, "Skeleton Barrel", 14, null, 3, "common", null),
                "Knight", new CardsResponse.Card(2, "Knight", 14, null, 3, "common", null)));
        when(cardUsageService.firstOn(any())).thenReturn(Optional.empty());
        when(cardUsageService.lastBefore(Instant.parse("2026-10-06T00:00:00Z")))
                .thenReturn(Optional.of(usage("2026-10-05T07:00:00Z", List.of(1, 2))));
        when(cardUsageService.lastBefore(Instant.parse("2026-10-19T00:00:00Z")))
                .thenReturn(Optional.of(usage("2026-10-18T07:00:00Z", List.of(2))));
        when(cardUsageService.current()).thenReturn(Optional.of(usage("2026-10-20T07:00:00Z", List.of(1))));
        BalanceService service = new BalanceService(updates, Map.of(), cardUsageService, cardService);

        BalanceUsage october6 = service.usage(service.update(LocalDate.of(2026, 10, 6)).orElseThrow());

        assertEquals(List.of(new BalanceUsage.Point(BalanceUsage.PointKind.BEFORE_NEXT, 0, 0.0)),
                october6.of("Skeleton Barrel", CardForm.NORMAL).after());
        assertEquals(List.of(new BalanceUsage.Point(BalanceUsage.PointKind.NOW, 0, 0.0)),
                october6.of("Knight", CardForm.NORMAL).after());
        assertEquals(50.0, october6.of("Knight", CardForm.NORMAL).before());
    }
}
