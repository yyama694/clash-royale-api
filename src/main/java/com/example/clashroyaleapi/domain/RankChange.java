package com.example.clashroyaleapi.domain;

/**
 * 個人ランキングの順位が、比べる記録からどれだけ動いたか。
 * 記録は上位1000人分だけなので、記録に居なかった人は「圏外から入った(NEW)」とする。
 *
 * @param places 動いた順位の数(上がったときも下がったときも正の数)。NEW と SAME では 0
 */
public record RankChange(Kind kind, int places) {

    public enum Kind {
        UP, DOWN, SAME, NEW
    }

    /** previousRank は比べる記録での順位。記録に居なければ null。 */
    public static RankChange of(int currentRank, Integer previousRank) {
        if (previousRank == null) {
            return new RankChange(Kind.NEW, 0);
        }
        int diff = previousRank - currentRank;
        if (diff > 0) {
            return new RankChange(Kind.UP, diff);
        }
        if (diff < 0) {
            return new RankChange(Kind.DOWN, -diff);
        }
        return new RankChange(Kind.SAME, 0);
    }
}
