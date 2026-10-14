package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.client.exception.CardNotFoundException;
import com.example.clashroyaleapi.domain.CardUsage;
import com.example.clashroyaleapi.domain.Rarity;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CardService {

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

    /** 通常のカードとタワーユニットの両方を、IDで引けるようにする。 */
    public Map<Integer, CardsResponse.Card> allById() {
        CardsResponse cards = apiClient.getCards();
        return Stream.concat(cards.items().stream(), cards.supportItems().stream())
                .collect(Collectors.toMap(CardsResponse.Card::id, card -> card, (first, second) -> first));
    }

    /** 公式APIの英語名で引く(バランス調整の内容は英語名で書いているため)。 */
    public Map<String, CardsResponse.Card> cardsByName() {
        return apiClient.getCards().items().stream()
                .collect(Collectors.toMap(CardsResponse.Card::name, card -> card, (first, second) -> first));
    }

    /**
     * カード一覧をレアリティごとに分け、各グループ内はエリクサーの軽い順に並べる。
     * タワーユニットはレアリティを持つが、デッキのカードとは別枠なので、レアリティに関係なく最後の1グループにまとめる。
     */
    public List<CardGroup> catalog() {
        CardsResponse cards = apiClient.getCards();
        List<CardGroup> groups = new ArrayList<>();
        cards.items().stream()
                .sorted(Comparator.comparingInt((CardsResponse.Card card) -> Rarity.rankOf(card.rarity()))
                        .thenComparing(BY_ELIXIR))
                .collect(Collectors.groupingBy(card -> Objects.requireNonNullElse(card.rarity(), ""), LinkedHashMap::new, Collectors.toList()))
                .forEach((rarity, members) -> groups.add(new CardGroup(rarity, members)));
        if (!cards.supportItems().isEmpty()) {
            groups.add(new CardGroup(null, cards.supportItems().stream().sorted(BY_ELIXIR).toList()));
        }
        return groups;
    }

    /**
     * カード一覧を、世界トップ層で使っていた人数の多い順に並べる(使用率ランキング)。同じ人数ならレアリティ別の一覧と同じ順。
     * 通常のカードとタワーユニットはデッキの別枠なので、グループを分ける(順位も別々に数える{@link CardUsage}と同じ)。
     * 返すのは通常のカードを全部まとめた1グループ(rarity は空文字)と、最後のタワーユニットのグループ(rarity は null)。
     */
    public List<CardGroup> byUsage(CardUsage usage) {
        Comparator<CardsResponse.Card> mostUsedFirst = Comparator
                .comparingInt((CardsResponse.Card card) -> usage.usageOf(card.id()).users())
                .reversed();
        List<CardGroup> catalog = catalog();
        List<CardGroup> groups = new ArrayList<>();
        groups.add(new CardGroup("", catalog.stream()
                .filter(group -> group.rarity() != null)
                .flatMap(group -> group.cards().stream())
                .sorted(mostUsedFirst)
                .toList()));
        catalog.stream()
                .filter(group -> group.rarity() == null)
                .forEach(tower -> groups.add(new CardGroup(null, tower.cards().stream().sorted(mostUsedFirst).toList())));
        return groups;
    }

    /**
     * エリクサーでの絞り込みに出す選択肢。
     * 将来コストの上限が変わっても付いていけるよう、固定値ではなくカード一覧(キャッシュ済み)から拾う。
     * 鏡とタワーユニットはコストを持たないため、ここには現れない。
     */
    public List<Integer> elixirCosts() {
        return apiClient.getCards().items().stream()
                .map(CardsResponse.Card::elixirCost)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }
}
