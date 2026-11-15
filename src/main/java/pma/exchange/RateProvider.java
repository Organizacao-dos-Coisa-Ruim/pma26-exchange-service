package pma.exchange;

import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import feign.FeignException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Busca a cotação de um par na API externa e traduz os erros dela para HTTP.
 *
 * Fica numa classe separada do ExchangeService de propósito: o cache do Spring funciona por proxy,
 * então o @Cacheable só intercepta chamadas vindas de OUTRA classe. Se fetch estivesse dentro do
 * ExchangeService, o cache seria ignorado.
 */
@Slf4j
@Component
public class RateProvider {

  private final AwesomeApiClient awesomeApiClient;
  /**
   * Quantas vezes fomos de fato à AwesomeAPI (no Prometheus: exchange_external_calls_total).
   * É a evidência do cache: sem ele, cresce junto com as requisições; com ele, quase não cresce.
   */
  private final Counter externalCalls;

  public RateProvider(AwesomeApiClient awesomeApiClient, MeterRegistry meterRegistry) {
    this.awesomeApiClient = awesomeApiClient;
    this.externalCalls = Counter.builder("exchange.external.calls")
        .description("Chamadas à API externa de cotações (AwesomeAPI)")
        .register(meterRegistry);
  }

  /**
   * @param from código já validado e em maiúsculas (ex.: "USD")
   * @param to   código já validado e em maiúsculas (ex.: "BRL")
   * @throws ResponseStatusException 404 se o par não existe; 503 se a API externa falhar ou demorar
   *
   * Cache: a chave é o par ("USD-BRL"). Na primeira chamada (miss) vai à AwesomeAPI e guarda a Rate
   * no Redis; nas seguintes, até o TTL vencer (5 min), devolve do Redis sem chamar a API (hit).
   * Exceções não são cacheadas: um 404 ou 503 tenta a API de novo na próxima requisição.
   * sync = true: se várias requisições do mesmo par chegam com o cache vazio, só uma vai à API
   * e as outras esperam o resultado (evita o "cache stampede" no início do load test).
   */
  @Cacheable(cacheNames = CacheConfig.RATES, key = "#from + '-' + #to", sync = true)
  public Rate fetch(String from, String to) {
    Map<String, AwesomeQuote> body;
    externalCalls.increment();
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
