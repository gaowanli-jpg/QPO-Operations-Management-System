package com.machining.quote;

import com.machining.quote.util.DrawingPartNoResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DrawingPartNoResolverTests {
    private static DrawingPartNoResolver.DrawingIdentity identity(String partNo, String version) {
        return new DrawingPartNoResolver.DrawingIdentity(partNo, version);
    }

    @Test
    void matchesDirectPartNoFilename() {
        String result = DrawingPartNoResolver.extractPartNo(
                "L20100002619.pdf",
                Set.of("L20100002619")
        );

        assertThat(result).isEqualTo("L20100002619");
    }

    @Test
    void keepsDashInsidePartNoInsteadOfTreatingItAsDescription() {
        String result = DrawingPartNoResolver.extractPartNo(
                "LK17.5.1.1-1.pdf",
                Set.of("LK17.5.1.1")
        );

        assertThat(result).isEqualTo("LK17.5.1.1-1");
    }

    @Test
    void matchesPartNoBeforeSpaceSeparatedDescription() {
        String result = DrawingPartNoResolver.extractPartNo(
                "L20100002619 B042动子安装座合盖.pdf",
                Set.of("L20100002619")
        );

        assertThat(result).isEqualTo("L20100002619");
    }

    @Test
    void keepsDashSeparatedSuffixInsidePartNo() {
        String result = DrawingPartNoResolver.extractPartNo(
                "L20100000759-异形垫片2 -2.pdf",
                Set.of("L20100000759")
        );

        assertThat(result).isEqualTo("L20100000759-异形垫片2 -2");
    }

    @Test
    void longestKnownPrefixWins() {
        String result = DrawingPartNoResolver.extractPartNo(
                "ABC12 定位块.pdf",
                Set.of("ABC1", "ABC12")
        );

        assertThat(result).isEqualTo("ABC12");
    }

    @Test
    void doesNotTrimAsciiSuffixWithoutBoundary() {
        String result = DrawingPartNoResolver.extractPartNo(
                "ABC1A.pdf",
                Set.of("ABC1")
        );

        assertThat(result).isEqualTo("ABC1A");
    }

    @Test
    void matchesPartNoAndVersionFilename() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P-100_A.pdf",
                Set.of(identity("P-100", "A"))
        );

        assertThat(result).isEqualTo(identity("P-100", "A"));
    }

    @Test
    void doesNotTreatDashAfterVersionAsDescriptionBoundary() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P-100_A-LOCATOR.pdf",
                Set.of(identity("P-100", "A"))
        );

        assertThat(result).isNull();
    }

    @Test
    void matchesPartNoAndVersionBeforeSpaceSeparatedDescription() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P-100_A 定位块.pdf",
                Set.of(identity("P-100", "A"))
        );

        assertThat(result).isEqualTo(identity("P-100", "A"));
    }

    @Test
    void doesNotTreatDashSeparatedSuffixAfterVersionAsDescription() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P-100_A-定位块.pdf",
                Set.of(identity("P-100", "A"))
        );

        assertThat(result).isNull();
    }

    @Test
    void doesNotMatchLongerAsciiVersionSuffix() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P-100_A1.pdf",
                Set.of(identity("P-100", "A"))
        );

        assertThat(result).isNull();
    }

    @Test
    void matchesKnownIdentityWhenDashBelongsToVersion() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P_100_A-1.pdf",
                Set.of(identity("P_100", "A-1"))
        );

        assertThat(result).isEqualTo(identity("P_100", "A-1"));
    }

    @Test
    void matchesKnownIdentityWithDashVersionBeforeSpaceSeparatedDescription() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "P_100_A-1 定位块.pdf",
                Set.of(identity("P_100", "A-1"))
        );

        assertThat(result).isEqualTo(identity("P_100", "A-1"));
    }

    @Test
    void normalizesVersionForIdentityMatching() {
        DrawingPartNoResolver.DrawingIdentity result = DrawingPartNoResolver.extractPartNoVersion(
                "p-100_a.pdf",
                Set.of(identity(" p-100 ", " a "))
        );

        assertThat(DrawingPartNoResolver.normalizeVersion(" a ")).isEqualTo("A");
        assertThat(result).isEqualTo(identity("P-100", "A"));
    }
}
