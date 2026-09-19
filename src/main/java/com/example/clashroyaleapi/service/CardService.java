package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.CardNotFoundException;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CardService {

    /** ゲーム内の並びに合わせたレアリティの順。ここに無い新しいレアリティは末尾に回す。 */
    private static final List<String> RARITY_ORDER = List.of("common", "rare", "epic", "legendary", "champion");

    // 鏡のようにエリクサーが固定でないカードは elixirCost が無いので、同じグループの最後に回す。
    private static final Comparator<CardsResponse.Card> BY_ELIXIR = Comparator
            .comparing((CardsResponse.Card card) -> card.elixirCost() == null ? Integer.MAX_VALUE : card.elixirCost())
            .thenComparingInt(CardsResponse.Card::id);

    private final ClashRoyaleApiClient apiClient;

    public CardService(ClashRoyaleApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** rarity が null のグループはタワーユニット。 */
    public record CardGroup(String rarity, List<CardsResponse.Card> cards) {
    }

    /** 公式APIにカードを1枚だけ引くAPIは無いため、一覧(キャッシュ済み)から探す。 */
    public CardsResponse.Card byId(int id) {
        CardsResponse cards = apiClient.getCards();
        return Stream.concat(cards.items().stream(), cards.supportItems().stream())
                .filter(card -> card.id() == id)
                .findFirst()
                .orElseThrow(() -> new CardNotFoundException("card not found: " + id));
    }

    /**
     * カード一覧をレアリティごとに分け、各グループ内はエリクサーの軽い順に並べる。
     * タワーユニットはレアリティを持つが、デッキのカードとは別枠なので、レアリティに関係なく最後の1グループにまとめる。
     */
    public List<CardGroup> catalog() {
        CardsResponse cards = apiClient.getCards();
        List<CardGroup> groups = new ArrayList<>();
        cards.items().stream()
                .sorted(Comparator.comparingInt(CardService::rarityRank).thenComparing(BY_ELIXIR))
                .collect(Collectors.groupingBy(card -> Objects.requireNonNullElse(card.rarity(), ""), LinkedHashMap::new, Collectors.toList()))
                .forEach((rarity, members) -> groups.add(new CardGroup(rarity, members)));
        if (!cards.supportItems().isEmpty()) {
            groups.add(new CardGroup(null, cards.supportItems().stream().sorted(BY_ELIXIR).toList()));
        }
        return groups;
    }

    private static int rarityRank(CardsResponse.Card card) {
        int index = RARITY_ORDER.indexOf(card.rarity());
        return index < 0 ? RARITY_ORDER.size() : index;
    }
}
