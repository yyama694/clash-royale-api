package com.example.clashroyaleapi.domain;

/**
 * Wilson score interval(母比率の信頼区間)。勝率で並べるときに、対戦数の少ないものが偶然の全勝・全敗で
 * 上位に来ないよう、下限(全勝の側を抑える)・上限(全敗の側を抑える)で並べるのに使う。
 */
final class WilsonScore {

    // 1.96 は95%信頼区間に対応する。
    private static final double Z = 1.96;

    private WilsonScore() {
    }

    static double lowerBound(int wins, int battles) {
        return bound(wins, battles, -1);
    }

    static double upperBound(int wins, int battles) {
        return bound(wins, battles, 1);
    }

    private static double bound(int wins, int battles, int sign) {
        if (battles == 0) {
            return 0;
        }
        double n = battles;
        double p = wins / n;
        double denominator = 1 + Z * Z / n;
        double centre = p + Z * Z / (2 * n);
        double margin = Z * Math.sqrt(p * (1 - p) / n + Z * Z / (4 * n * n));
        return (centre + sign * margin) / denominator;
    }
}
