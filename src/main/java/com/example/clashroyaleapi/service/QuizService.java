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
import java.util.ArrayList;
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

    /** 「何発で倒せる?」を出し始める日(UTC)。公開した後の日の途中で問題が変わらないよう、翌日から出す。 */
    static final LocalDate HITS_FROM = LocalDate.of(2026, 11, 27);

    /** 「何発で倒せる?」の1発のダメージ。この3つのうちちょうど1つがあるカードだけが攻撃する側になる。 */
    static final List<String> HIT_DAMAGES = List.of("stat.TID_SPELL_ATTRIBUTE_DAMAGE",
            "stat.TID_SPELL_ATTRIBUTE_RANGED_DAMAGE", "stat.TID_SPELL_ATTRIBUTE_AREA_DAMAGE");

    /** 体力が半分になると別の動きになるカード(キャノンカート・ダイナマイトゴブリン)。倒される側にも出さない。 */
    private static final String KAMIKAZE = "stat.TID_KAMIKAZE_TRIGGER_HEALTH";

    /**
     * これがあるカードは攻撃する側に出さない。1発目や当たり方が違う(突進・ダッシュ・ジャンプ・連続攻撃・連鎖・貫通・
     * 出したときのダメージ)、体力が減ると別の動きになる(自爆)ため、「同じダメージを何発」で数えられない。
     */
    static final Set<String> HITS_EXCLUDED_ATTACKS = Set.of("stat.TID_SPELL_ATTRIBUTE_CHARGE_DAMAGE",
            "stat.TID_SPELL_ATTRIBUTE_DASH_DAMAGE", "stat.TID_SPELL_ATTRIBUTE_JUMP_DAMAGE",
            "stat.TID_SPELL_ATTRIBUTE_COMBO_DAMAGE", "stat.TID_TYPE_CHAINED_ATTACKS",
            "stat.TID_SPELL_ATTRIBUTE_BULLET_RANGE", "stat.TID_SPELL_ATTRIBUTE_SPAWN_DAMAGE", KAMIKAZE);

    /** 攻撃の間隔。これが無いカード(ファイアスピリット・ウォールブレイカーなど、1回当たって消える)は攻撃する側に出さない。 */
    private static final String HIT_SPEED = "stat.TID_SPELL_ATTRIBUTE_HIT_SPEED";

    /**
     * 個別に外すカード。三銃士は近くの相手には別のダメージで殴る。ローニンは攻撃を受けると反撃する。
     * バトルヒーラーは攻撃するたびに自分が回復するので、倒される側の何発が変わる。
     */
    static final Set<String> HITS_EXCLUDED_ATTACKERS = Set.of("Three Musketeers", "Ronin");
    static final Set<String> HITS_EXCLUDED_DEFENDERS = Set.of("Battle Healer", "Ronin");

    /** 何発がちょうど割り切れる近く(1発のこの割合(%)以内)の組は、値の読み違いで答えがずれうるので出さない。 */
    static final int HITS_MARGIN_PERCENT = 3;

    private static final String TARGET = "stat.TID_SPELL_ATTRIBUTE_TARGET";
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
        LocalDate today = todayUtc();
        List<DailyQuiz.Matchup> matchups = today.isBefore(HITS_FROM) ? List.of() : matchups(stats, apiCards);
        return DailyQuiz.of(today, FIRST_DAY, cards, units, spells, abilities, matchups);
    }

    /** 今日の回。問題は作らない(トップページの案内に使う)。 */
    public DailyQuiz.Round todayRound() {
        return DailyQuiz.round(todayUtc(), FIRST_DAY);
    }

    private LocalDate todayUtc() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    /**
     * 「何発で倒せる?」に出せる組。攻撃が当たる組(攻撃目標が建物なら建物だけ、地上なら空を飛ぶカード以外)で、
     * 何発(ヒットポイント÷1発のダメージの切り上げ)がレベル11とレベル16で同じで、HITS_MIN〜HITS_MAX の組。
     * シールドのあるカードは倒される側に出さない(シールドとヒットポイントでダメージの通り方が違う)。
     * 地上の形(スピリットエンプレス（地上）)は公式APIのカードに無いので出ない。
     */
    static List<DailyQuiz.Matchup> matchups(CardStats stats, List<CardsResponse.Card> apiCards) {
        record Side(CardsResponse.Card card, int health16, int health11) {
        }
        List<Side> sides = new ArrayList<>();
        for (CardsResponse.Card card : apiCards) {
            OptionalInt health16 = wholeNumber(stats.value16(card.name(), CardStats.HEALTH));
            OptionalInt health11 = wholeNumber(stats.value11(card.name(), CardStats.HEALTH));
            if (health16.isPresent() && health11.isPresent()) {
                sides.add(new Side(card, health16.getAsInt(), health11.getAsInt()));
            }
        }
        List<DailyQuiz.Matchup> matchups = new ArrayList<>();
        for (Side attacker : sides) {
            String name = attacker.card().name();
            List<String> damageKeys = HIT_DAMAGES.stream().filter(key -> stats.has(name, key)).toList();
            if (damageKeys.size() != 1 || !stats.has(name, HIT_SPEED) || HITS_EXCLUDED_ATTACKERS.contains(name)
                    || HITS_EXCLUDED_ATTACKS.stream().anyMatch(key -> stats.has(name, key))) {
                continue;
            }
            OptionalInt damage16 = wholeNumber(stats.value16(name, damageKeys.getFirst()));
            OptionalInt damage11 = wholeNumber(stats.value11(name, damageKeys.getFirst()));
            if (damage16.isEmpty() || damage11.isEmpty()) {
                continue;
            }
            String target = stats.value16(name, TARGET).orElse("");
            for (Side defender : sides) {
                String defenderName = defender.card().name();
                if (defender == attacker || HITS_EXCLUDED_DEFENDERS.contains(defenderName)
                        || stats.has(defenderName, CardStats.SHIELD_HEALTH) || stats.has(defenderName, KAMIKAZE)
                        || !canHit(target, stats.has(defenderName, CardStats.LIFETIME), FLYING.contains(defenderName))) {
                    continue;
                }
                int hits = ceilDiv(defender.health16(), damage16.getAsInt());
                if (hits < DailyQuiz.HITS_MIN || hits > DailyQuiz.HITS_MAX
                        || hits != ceilDiv(defender.health11(), damage11.getAsInt())
                        || nearMultiple(defender.health16(), damage16.getAsInt())
                        || nearMultiple(defender.health11(), damage11.getAsInt())) {
                    continue;
                }
                matchups.add(new DailyQuiz.Matchup(attacker.card().id(), attacker.health16(), defender.card().id(),
                        defender.health16(), damage16.getAsInt(), hits));
            }
        }
        return List.copyOf(matchups);
    }

    private static boolean canHit(String target, boolean building, boolean flying) {
        if (target.contains("TID_TARGETS_BUILDINGS")) {
            return building;
        }
        if (target.contains("TID_TARGETS_AIR_AND_GROUND")) {
            return true;
        }
        return target.contains("TID_TARGETS_GROUND") && !flying;
    }

    private static int ceilDiv(int health, int damage) {
        return (health + damage - 1) / damage;
    }

    /** ヒットポイントが1発のダメージのちょうど倍数か、その前後 HITS_MARGIN_PERCENT 以内。 */
    private static boolean nearMultiple(int health, int damage) {
        long rest = health % damage;
        long distance = Math.min(rest, damage - rest);
        return distance * 100 <= (long) damage * HITS_MARGIN_PERCENT;
    }

    private static OptionalInt wholeNumber(Optional<String> value) {
        return value.filter(v -> v.matches("\\d+")).map(v -> OptionalInt.of(Integer.parseInt(v)))
                .orElse(OptionalInt.empty());
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
