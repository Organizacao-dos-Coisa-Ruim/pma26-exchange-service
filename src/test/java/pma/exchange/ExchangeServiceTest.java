package pma.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** Testes unitários do ExchangeService: validação das moedas, com o RateProvider mockado. */
@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

  @Mock
  private RateProvider rateProvider;

  @InjectMocks
  private ExchangeService exchangeService;

  @Test
  void quoteDevolveCotacaoComAConta() {
    when(rateProvider.fetch("USD", "BRL"))
        .thenReturn(new Rate(new BigDecimal("5.668"), new BigDecimal("5.6666"), "2026-11-13 18:30:13"));

    QuoteOut out = exchangeService.quote("USD", "BRL", "conta-1");

    assertThat(out.sell()).isEqualByComparingTo("5.668");
    assertThat(out.buy()).isEqualByComparingTo("5.6666");
    assertThat(out.date()).isEqualTo("2026-11-13 18:30:13");
    assertThat(out.idAccount()).isEqualTo("conta-1");
  }

  @Test
  void quoteAceitaMinusculas() {
    when(rateProvider.fetch("USD", "BRL"))
        .thenReturn(new Rate(BigDecimal.ONE, BigDecimal.ONE, "2026-11-13 18:30:13"));

    exchangeService.quote("usd", " brl ", null);

    verify(rateProvider).fetch("USD", "BRL");
  }

  @Test
  void moedasIguaisDa400() {
    assertStatus(() -> exchangeService.quote("USD", "usd", null), HttpStatus.BAD_REQUEST);
    verify(rateProvider, never()).fetch(any(), any());
  }

  @Test
  void codigoInvalidoDa400() {
    assertStatus(() -> exchangeService.quote("US", "BRL", null), HttpStatus.BAD_REQUEST);
    assertStatus(() -> exchangeService.quote("USD", "BR1", null), HttpStatus.BAD_REQUEST);
    assertStatus(() -> exchangeService.quote(null, "BRL", null), HttpStatus.BAD_REQUEST);
    verify(rateProvider, never()).fetch(any(), any());
  }

  private static void assertStatus(Runnable call, HttpStatusCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(expected));
  }

}
