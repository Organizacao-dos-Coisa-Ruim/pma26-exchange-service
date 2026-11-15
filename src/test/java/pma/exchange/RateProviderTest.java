package pma.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;

/** Testes unitários do RateProvider: a API externa (AwesomeApiClient) é mockada. */
@ExtendWith(MockitoExtension.class)
class RateProviderTest {

  @Mock
  private AwesomeApiClient awesomeApiClient;

  @InjectMocks
  private RateProvider rateProvider;

  @Test
  void fetchConverteARespostaDaApi() {
    when(awesomeApiClient.last("USD-BRL")).thenReturn(Map.of("USDBRL",
        new AwesomeQuote("USD", "BRL", "5.6666", "5.668", "2026-11-13 18:30:13")));

    Rate rate = rateProvider.fetch("USD", "BRL");

    // sell = ask (venda), buy = bid (compra)
    assertThat(rate.sell()).isEqualByComparingTo("5.668");
    assertThat(rate.buy()).isEqualByComparingTo("5.6666");
    assertThat(rate.date()).isEqualTo("2026-11-13 18:30:13");
  }

  @Test
  void parInexistenteDa404() {
    FeignException notFound = feignError(404);
    when(awesomeApiClient.last("USD-XYZ")).thenThrow(notFound);

    assertStatus(() -> rateProvider.fetch("USD", "XYZ"), HttpStatus.NOT_FOUND);
  }

  @Test
  void respostaSemOParDa404() {
    when(awesomeApiClient.last("USD-BRL")).thenReturn(Map.of());

    assertStatus(() -> rateProvider.fetch("USD", "BRL"), HttpStatus.NOT_FOUND);
  }

  @Test
  void timeoutDa503() {
    // timeout / conexão recusada: o Feign lança uma exceção sem status HTTP (-1)
    FeignException timeout = feignError(-1);
    when(awesomeApiClient.last("USD-BRL")).thenThrow(timeout);

    assertStatus(() -> rateProvider.fetch("USD", "BRL"), HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void erro5xxDaApiDa503() {
    FeignException error = feignError(500);
    when(awesomeApiClient.last("USD-BRL")).thenThrow(error);

    assertStatus(() -> rateProvider.fetch("USD", "BRL"), HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void cotacaoInvalidaDa503() {
    when(awesomeApiClient.last("USD-BRL")).thenReturn(Map.of("USDBRL",
        new AwesomeQuote("USD", "BRL", "abc", null, "2026-11-13 18:30:13")));

    assertStatus(() -> rateProvider.fetch("USD", "BRL"), HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static FeignException feignError(int status) {
    FeignException e = mock(FeignException.class);
    when(e.status()).thenReturn(status);
    return e;
  }

  private static void assertStatus(Runnable call, HttpStatusCode expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(expected));
  }

}
