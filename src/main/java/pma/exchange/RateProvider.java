package pma.exchange;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Busca a cotação de um par na API externa e traduz os erros dela para HTTP.
 *
 * Fica numa classe separada do ExchangeService de propósito: na issue #76 o @Cacheable
 * entra no método fetch. O cache do Spring funciona por proxy, então só intercepta chamadas
 * vindas de OUTRA classe; se fetch estivesse dentro do ExchangeService, o cache seria ignorado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateProvider {

  private final AwesomeApiClient awesomeApiClient;

  /**
   * @param from código já validado e em maiúsculas (ex.: "USD")
   * @param to   código já validado e em maiúsculas (ex.: "BRL")
   * @throws ResponseStatusException 404 se o par não existe; 503 se a API externa falhar ou demorar
   */
  public Rate fetch(String from, String to) {
    Map<String, AwesomeQuote> body;
    try {
      body = awesomeApiClient.last(from + "-" + to);
    } catch (FeignException e) {
      // status() == 404: par inexistente. Timeout/conexão recusada vêm com status -1.
      if (e.status() == HttpStatus.NOT_FOUND.value()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "par de moedas não encontrado: " + from + "-" + to);
      }
      log.warn("AwesomeAPI falhou para {}-{}: status {}: {}", from, to, e.status(), e.getMessage());
      throw unavailable();
    }

    AwesomeQuote quote = body == null ? null : body.get(from + to);
    if (quote == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "par de moedas não encontrado: " + from + "-" + to);
    }
    try {
      return QuoteParser.toRate(quote);
    } catch (RuntimeException e) {
      log.warn("AwesomeAPI devolveu cotação inválida para {}-{}: {}", from, to, quote);
      throw unavailable();
    }
  }

  private static ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "fonte de cotações indisponível");
  }

}
