package com.saas.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PricingCalculatorTest {

    private static final LocalDate NOVEMBRO = LocalDate.of(2025, 11, 15);
    private static final LocalDate OUTUBRO = LocalDate.of(2025, 10, 15);

    private final PricingCalculator calculator = new PricingCalculator();

    private BillingCommand command(String basePrice, LocalDate signupDate, int overdueDays,
                                   String promoCode, boolean nonProfit) {
        return new BillingCommand("cus_1", Money.of(basePrice), signupDate, overdueDays,
                promoCode, nonProfit, "tok_123");
    }

    private Money calcular(String basePrice, LocalDate signupDate, LocalDate hoje) {
        return calculator.calculate(command(basePrice, signupDate, 0, null, false), hoje);
    }

    @Test
    void clienteNovoPagaPrecoCheio() {
        assertEquals(Money.of("100.00"), calcular("100.00", LocalDate.of(2025, 11, 1), NOVEMBRO));
    }

    @Test
    void descontoDeFidelidadePorTempoDeAssinatura() {
        assertEquals(Money.of("100.00"), calcular("100.00", LocalDate.of(2024, 11, 1), NOVEMBRO)); // 12 meses
        assertEquals(Money.of("90.00"), calcular("100.00", LocalDate.of(2024, 10, 1), NOVEMBRO));  // 13 meses
        assertEquals(Money.of("90.00"), calcular("100.00", LocalDate.of(2023, 11, 1), NOVEMBRO));  // 24 meses
        assertEquals(Money.of("85.00"), calcular("100.00", LocalDate.of(2023, 10, 1), NOVEMBRO));  // 25 meses
    }

    @Test
    void mesesSaoContadosPeloCalendarioIgnorandoODia() {
        // 31/10/2024 até 01/11/2025 dá 12 meses completos, mas o legado conta 13
        LocalDate hoje = LocalDate.of(2025, 11, 1);
        assertEquals(Money.of("90.00"), calcular("100.00", LocalDate.of(2024, 10, 31), hoje));
    }

    @Test
    void ongTem30PorCentoDeDescontoDepoisDaFidelidade() {
        Money semFidelidade = calculator.calculate(
                command("100.00", LocalDate.of(2025, 11, 1), 0, null, true), NOVEMBRO);
        Money comFidelidade = calculator.calculate(
                command("100.00", LocalDate.of(2023, 10, 1), 0, null, true), NOVEMBRO);

        assertEquals(Money.of("70.00"), semFidelidade);
        assertEquals(Money.of("59.50"), comFidelidade);
    }

    @Test
    void blackFridaySoValeEmNovembro() {
        BillingCommand blackFriday = command("100.00", LocalDate.of(2025, 1, 1), 0, "BLACKFRIDAY", false);
        BillingCommand minusculo = command("100.00", LocalDate.of(2025, 1, 1), 0, "blackfriday", false);

        assertEquals(Money.of("70.00"), calculator.calculate(blackFriday, NOVEMBRO));
        assertEquals(Money.of("70.00"), calculator.calculate(minusculo, NOVEMBRO));
        assertEquals(Money.of("100.00"), calculator.calculate(blackFriday, OUTUBRO));
    }

    @Test
    void welcome10SoValeAteOPrimeiroMes() {
        BillingCommand primeiroMes = command("100.00", LocalDate.of(2025, 10, 1), 0, "WELCOME10", false);
        BillingCommand segundoMes = command("100.00", LocalDate.of(2025, 9, 1), 0, "WELCOME10", false);

        assertEquals(Money.of("90.00"), calculator.calculate(primeiroMes, NOVEMBRO));
        assertEquals(Money.of("100.00"), calculator.calculate(segundoMes, NOVEMBRO));
    }

    @Test
    void cupomDesconhecidoNaoDaDesconto() {
        BillingCommand cupom = command("100.00", LocalDate.of(2025, 11, 1), 0, "QUALQUER", false);

        assertEquals(Money.of("100.00"), calculator.calculate(cupom, NOVEMBRO));
    }

    @Test
    void atrasoCobraMultaDe2PorCentoMaisJurosDiarios() {
        LocalDate signup = LocalDate.of(2025, 11, 1);

        // 100 + 2 de multa + 100 * 0,001 * 10 dias de juros
        assertEquals(Money.of("103.00"), calculator.calculate(command("100.00", signup, 10, null, false), NOVEMBRO));
        assertEquals(Money.of("100.00"), calculator.calculate(command("100.00", signup, 0, null, false), NOVEMBRO));
        assertEquals(Money.of("100.00"), calculator.calculate(command("100.00", signup, -5, null, false), NOVEMBRO));
    }

    @Test
    void precoNuncaFicaNegativo() {
        LocalDate signup = LocalDate.of(2025, 1, 1);

        assertEquals(Money.ZERO, calculator.calculate(command("20.00", signup, 0, "BLACKFRIDAY", false), NOVEMBRO));
        assertEquals(Money.ZERO, calculator.calculate(command("20.00", signup, 10, "BLACKFRIDAY", false), NOVEMBRO));
    }

    @Test
    void combinacaoDeRegrasDaOMesmoValorDoCodigoLegado() {
        // fidelidade de 1 ano + ONG + 3 dias de atraso
        BillingCommand cmd = command("99.99", LocalDate.of(2024, 10, 1), 3, null, true);

        double legado = 99.99 - (99.99 * 0.10);
        legado = legado * 0.70;
        legado = legado + (legado * 0.02) + (legado * 0.001) * 3;

        Money resultado = calculator.calculate(cmd, NOVEMBRO);

        assertEquals(legado, resultado.amount().doubleValue(), 0.000001);
        assertEquals("64.44", resultado.toString());
    }
}
