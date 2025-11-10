// src/main/java/com/hamas/reviewtrust/domain/scoring/profile/DefaultScoringProfileRepository.java
package com.hamas.reviewtrust.domain.scoring.profile;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 既定の ScoringProfile リポジトリ実装。
 * プロファイル未配備でも起動を止めないため、既定インスタンスを返す。
 */
@Component
@Primary
public class DefaultScoringProfileRepository extends ScoringProfileRepository {

    @Override
    public ScoringProfile get() {
        // 1) static defaults() があれば最優先で利用
        try {
            var m = ScoringProfile.class.getMethod("defaults");
            Object o = m.invoke(null);
            return (ScoringProfile) o;
        } catch (Throwable ignore) { /* fallthrough */ }

        // 2) no-arg コンストラクタがあればそれで生成
        try {
            var ctor = ScoringProfile.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable e) {
            throw new IllegalStateException(
                "ScoringProfile の既定インスタンスを生成できません。" +
                "defaults() か no-arg コンストラクタを用意するか、専用の Repository 実装に差し替えてください。", e);
        }
    }
}
