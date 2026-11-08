package com.example.clashroyaleapi.service;

import com.example.clashroyaleapi.client.ClashRoyaleApiClient;
import com.example.clashroyaleapi.client.dto.CardsResponse;
import com.example.clashroyaleapi.domain.CardStats;
import com.example.clashroyaleapi.domain.DailyQuiz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * クラロワクイズの今日の問題。1日はUTCで区切る(日本時間 9:00に替わる)。世界で同じ瞬間に替わり、「第〇回」が全員でそろうため。
 * 出題するのは公式APIのカード一覧(キャッシュ済み)のうちエリクサーが決まっているカードで、鏡とタワーユニットは入らない。
 * 公式APIのエリクサーは、2026-10-27に全122枚を照らして一致を確かめた(新しいカードと値に確信の持てないカードは、
 * 10/25にゲーム内で撮ったカード情報の画面で確かめた)。
 */
@Service
public class QuizService {

    /** 第1回の日。公開した日(11/2にユーザーが「公開日を第1回」と決定。隠し版は10/27から出していた)。 */
    static final LocalDate FIRST_DAY = LocalDate.of(2026, 11, 3);

    /**
     * 空を飛ぶカード(公式APIの英語名)。地上にしか当たらない呪文の問題には出さない。ゲームのデータに「空を飛ぶか」の
     * 項目が無いので、ユーザーに確かめた一覧(2026-11-01)。新しい空のカードが出たら足す。
     * スピリットエンプレスは霊龍にまたがった形(地上の形は並べ替えにも呪文の問題にも出ない)。
     */
    static final Set<String> FLYING = Set.of("Minions", "Minion Horde", "Mega Minion", "Bats", "Baby Dragon",
            "Inferno Dragon", "Electro Dragon", "Skeleton Dragons", "Balloon", "Lava Hound", "Flying Machine", "Phoenix",
            "Skeleton Barrel", "Spirit Empress", "Minion Giant");

    /** 空を飛ぶユニット(cards/units.tsv の「カード.ユニット」)。フェニックスの卵などほかのユニットは地上。 */
    static final Set<String> FLYING_UNITS = Set.of("Lava Hound.lavaPup");

    /**
     * 「呪文一撃で倒せるのはどれ?」に出す呪文(2026-11-01にユーザーが決定)。一度にダメージを与える呪文はその値、
     * 時間で削る呪文は効果時間いっぱい当てたときの合計(毎秒ダメージ×効果時間)。ボイドは当たった数でダメージが変わり、
     * 答えが一つに決まらないので出さない。
     */
    static final List<String> HIT_SPELLS = List.of("Zap", "The Log", "Barbarian Barrel", "Arrows", "Giant Snowball",
            "Royal Delivery", "Fireball", "Lightning", "Rocket", "Freeze", "Rage", "Tornado");
    static final List<String> OVER_TIME_SPELLS = List.of("Poison", "Earthquake", "Goblin Curse", "Vines");

    /** 地上にしか当たらない呪文。アースクエイクはステータスに攻撃目標の行が無いので、名前で持つ。 */
    static final Set<String> GROUND_ONLY_SPELLS = Set.of("The Log", "Barbarian Barrel", "Earthquake");

    private static final String AREA_DAMAGE = "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE";
    private static final String DAMAGE = "stat.TID_SPELL_ATTRIBUTE_DAMAGE";
    private static final String DAMAGE_PER_SECOND = "stat.TID_SPELL_ATTRIBUTE_DAMAGE_PER_SECOND";
    private static final String BUILDING_DPS = "stat.TID_SPELL_ATTRIBUTE_BUILDING_DPS";
    private static final String DURATION = "stat.TID_SPELL_ATTRIBUTE_DURATION";
    private static final String CURSE_DURATION = "stat.TID_SPELL_ATTRIBUTE_CURSE_DURATION";
    // 矢の雨の「196 x3」のように、回数の付いたダメージ
    private static final Pattern HITS = Pattern.compile("(\\d+)(?: x(\\d+))?");
    private static final Pattern SECONDS = Pattern.compile("\\{s:([\\d.]+)}");

    private final ClashRoyaleApiClient apiClient;
    private final CardStatsService cardStatsService;
    private final Clock clock;

    @Autowired
    public QuizService(ClashRoyaleApiClient apiClient, CardStatsService cardStatsService) {
        this(apiClient, cardStatsService, Clock.systemUTC());
    }

    QuizService(ClashRoyaleApiClient apiClient, CardStatsService cardStatsService, Clock clock) {
        this.apiClient = apiClient;
        this.cardStatsService = cardStatsService;
        this.clock = clock;
    }

    /**
     * 地上に出すと別のコストになるカード(スピリットエンプレス)は、地上の形も別のカードとして出す。
     * 地上のコストは公式APIに無いので、ゲームの画面から読み取ったステータスの「エリクサー（地上）」を使う。
     * ヒットポイントの並べ替えも、ステータスのヒットポイントで出す(地上の形は霊龍の形と同じ値なので出さない)。
     * 何体も出るカード(1体あたりの値)やシールドのあるカード(シールドを含まない値)も出し、どの値かは画面で注釈を添える
     * (ViewMapper。2026-10-31にユーザーが決定)。
     * カードとは別にヒットポイントを持つユニット(cards/units.tsv。ゴブリンシュタインのモンスターなど)も、並べ替えに別のものとして出す。
     * 呪文の問題の呪文のダメージも、ステータスから出す(レベル16)。
     * スキルのコストの問題は、ゲームの画面から読み取ったヒーローとチャンピオンのスキルのコスト(cards/abilities.tsv)で出す。
     */
    public DailyQuiz today() {
        CardStats stats = cardStatsService.stats();
        List<CardsResponse.Card> apiCards = apiClient.getCards().items();
        Map<String, Integer> ids = apiCards.stream()
                .collect(Collectors.toMap(CardsResponse.Card::name, CardsResponse.Card::id, (a, b) -> a));
        List<DailyQuiz.Unit> units = cardStatsService.units().stream()
                .filter(unit -> ids.containsKey(unit.card()))
                .map(unit -> new DailyQuiz.Unit(ids.get(unit.card()), unit.unit(), unit.health(),
                        FLYING_UNITS.contains(unit.card() + "." + unit.unit())))
                .toList();
        List<DailyQuiz.Card> cards = apiCards.stream()
                .filter(card -> card.elixirCost() != null)
                .flatMap(card -> {
                    OptionalInt health = stats.health(card.name());
                    DailyQuiz.Card normal = new DailyQuiz.Card(card.id(), false, card.elixirCost(),
                            health.isPresent() ? health.getAsInt() : null, FLYING.contains(card.name()),
                            stats.has(card.name(), CardStats.LIFETIME));
                    OptionalInt ground = stats.groundElixir(card.name());
                    return ground.isPresent()
                            ? Stream.of(normal, new DailyQuiz.Card(card.id(), true, ground.getAsInt()))
                            : Stream.of(normal);
                })
                .toList();
        List<DailyQuiz.Spell> spells = apiCards.stream()
                .map(card -> spell(stats, card))
                .flatMap(Optional::stream)
                .toList();
        List<DailyQuiz.Ability> abilities = cardStatsService.abilities().stream()
                .filter(ability -> ids.containsKey(ability.card()))
                .map(ability -> new DailyQuiz.Ability(ids.get(ability.card()), ability.champion(), ability.cost()))
                .toList();
        return DailyQuiz.of(todayUtc(), FIRST_DAY, cards, units, spells, abilities);
    }

    /** 今日の回。問題は作らない(トップページの案内に使う)。 */
    public DailyQuiz.Round todayRound() {
        return DailyQuiz.round(todayUtc(), FIRST_DAY);
    }

    private LocalDate todayUtc() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    /** 呪文の問題に出す呪文でなければ、またはステータスにダメージが無ければ empty。 */
    static Optional<DailyQuiz.Spell> spell(CardStats stats, CardsResponse.Card card) {
        String name = card.name();
        boolean groundOnly = GROUND_ONLY_SPELLS.contains(name);
        if (HIT_SPELLS.contains(name)) {
            OptionalInt damage = hits(stats.value16(name, AREA_DAMAGE).or(() -> stats.value16(name, DAMAGE)));
            return damage.isPresent()
                    ? Optional.of(new DailyQuiz.Spell(card.id(), damage.getAsInt(), damage.getAsInt(), groundOnly, false))
                    : Optional.empty();
        }
        if (OVER_TIME_SPELLS.contains(name)) {
            Optional<Double> seconds = seconds(stats.value16(name, DURATION).or(() -> stats.value16(name, CURSE_DURATION)));
            OptionalInt perSecond = hits(stats.value16(name, DAMAGE_PER_SECOND));
            if (seconds.isEmpty() || perSecond.isEmpty()) {
                return Optional.empty();
            }
            OptionalInt buildingPerSecond = hits(stats.value16(name, BUILDING_DPS));
            int damage = (int) (perSecond.getAsInt() * seconds.get());
            int buildingDamage = buildingPerSecond.isPresent()
                    ? (int) (buildingPerSecond.getAsInt() * seconds.get()) : damage;
            return Optional.of(new DailyQuiz.Spell(card.id(), damage, buildingDamage, groundOnly, true));
        }
        return Optional.empty();
    }

    private static OptionalInt hits(Optional<String> value) {
        Matcher m = HITS.matcher(value.orElse(""));
        if (!m.matches()) {
            return OptionalInt.empty();
        }
        int times = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
        return OptionalInt.of(Integer.parseInt(m.group(1)) * times);
    }

    private static Optional<Double> seconds(Optional<String> value) {
        Matcher m = SECONDS.matcher(value.orElse(""));
        return m.matches() ? Optional.of(Double.parseDouble(m.group(1))) : Optional.empty();
    }
}
