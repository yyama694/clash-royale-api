package com.example.clashroyaleapi.domain;

import com.example.clashroyaleapi.client.ApiTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/** クランメンバーの最終アクセスから、非アクティブ日数を求める。 */
public final class MemberActivity {

    private MemberActivity() {
    }

    public static OptionalLong inactiveDays(String lastSeen, Instant now) {
        Optional<Instant> seen = ApiTimestamp.parse(lastSeen);
        if (seen.isEmpty()) {
            return OptionalLong.empty();
        }
        // APIとサーバーの時刻がわずかにずれると負になりうるため0で下限を切る。
        return OptionalLong.of(Math.max(0, Duration.between(seen.get(), now).toDays()));
    }
}
