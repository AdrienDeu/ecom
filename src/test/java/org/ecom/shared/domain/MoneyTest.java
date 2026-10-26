package org.ecom.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToCents() {
        assertThat(Money.eur("19.995").amount()).isEqualByComparingTo("20.00");
        assertThat(Money.eur("19.985").amount()).isEqualByComparingTo("19.98");
    }

    @Test
    void multipliesAndAdds() {
        assertThat(Money.eur("19.99").times(3).plus(Money.eur("0.03"))).isEqualTo(Money.eur("60.00"));
    }

    @Test
    void subtractsAndCompares() {
        assertThat(Money.eur("10").minus(Money.eur("2.50"))).isEqualTo(Money.eur("7.50"));
        assertThat(Money.eur("10").isGreaterThan(Money.eur("9.99"))).isTrue();
        assertThat(Money.eur("10").isGreaterThan(Money.eur("10.00"))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Money.eur("1").minus(Money.eur("2")));
    }

    @Test
    void rejectsNegativeAmountsAndCurrencyMismatch() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.eur("-1"));
        assertThatIllegalArgumentException().isThrownBy(() -> Money.eur("1").plus(new Money(BigDecimal.ONE, "USD")));
    }

    @Test
    void slugsAreAsciiLowercase() {
        assertThat(Slugs.of("  Chaussures d'Été — 2026 ")).isEqualTo("chaussures-d-ete-2026");
    }

}
