package pma.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import feign.FeignException;

/**
 * Sobe a aplicação inteira com o cliente da API externa mockado (os testes não acessam a internet).
 * Perfil "test": cache em memória no lugar do Redis (application-test.yaml).
 */
// métricas ficam desligadas em testes por padrão; religamos para testar o endpoint do Prometheus
@AutoConfigureMetrics
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExchangeResourceIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AwesomeApiClient awesomeApiClient;

  @Autowired
  private CacheManager cacheManager;

  /** O contexto (e o cache) é compartilhado entre os testes: cada teste começa com o cache vazio. */
  @BeforeEach
  void limparCache() {
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
  }

  @Test
  void healthCheck() throws Exception {
    mockMvc.perform(get("/exchange/health-check")).andExpect(status().isOk());
  }

  @Test
  void metricasDoPrometheusComHistogramaDeLatencia() throws Exception {
    mockMvc.perform(get("/exchange/health-check"));

    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("http_server_requests_seconds_bucket")));
  }

  @Test
  void quoteDevolveOFormatoDoEnunciado() throws Exception {
    when(awesomeApiClient.last("USD-BRL")).thenReturn(Map.of("USDBRL",
        new AwesomeQuote("USD", "BRL", "5.6666", "5.668", "2026-11-13 18:30:13")));

    mockMvc.perform(get("/exchange/usd/brl").header("id-account", "conta-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sell").value(5.668))
        .andExpect(jsonPath("$.buy").value(5.6666))
        .andExpect(jsonPath("$.date").value("2026-11-13 18:30:13"))
        .andExpect(jsonPath("$['id-account']").value("conta-1"));
  }

  @Test
  void segundaCotacaoVemDoCache() throws Exception {
    when(awesomeApiClient.last("USD-BRL")).thenReturn(Map.of("USDBRL",
        new AwesomeQuote("USD", "BRL", "5.6666", "5.668", "2026-11-13 18:30:13")));

    // usuários diferentes, mesmo par: a cotação é compartilhada, o id-account não
    mockMvc.perform(get("/exchange/USD/BRL").header("id-account", "conta-1"))
        .andExpect(jsonPath("$['id-account']").value("conta-1"));
    mockMvc.perform(get("/exchange/usd/brl").header("id-account", "conta-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sell").value(5.668))
        .andExpect(jsonPath("$['id-account']").value("conta-2"));

    verify(awesomeApiClient, times(1)).last("USD-BRL");
  }

  @Test
  void requisicoesSimultaneasComCacheVazioChamamAApiUmaVez() throws Exception {
    // API lenta: as 10 requisições chegam enquanto a primeira ainda está esperando a resposta
    when(awesomeApiClient.last("EUR-BRL")).thenAnswer(invocation -> {
      Thread.sleep(300);
      return Map.of("EURBRL", new AwesomeQuote("EUR", "BRL", "6.2462", "6.2482", "2026-11-13 19:21:35"));
    });

    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        results.add(pool.submit(() -> mockMvc.perform(get("/exchange/EUR/BRL"))
            .andReturn().getResponse().getStatus()));
      }
      for (Future<Integer> result : results) {
        assertThat(result.get()).isEqualTo(200);
      }
    } finally {
      pool.shutdown();
    }

    // @Cacheable(sync = true): só uma thread vai à API, as outras esperam o valor no cache
    verify(awesomeApiClient, times(1)).last("EUR-BRL");
  }

  @Test
  void erroNaoECacheado() throws Exception {
    FeignException notFound = mock(FeignException.class);
    when(notFound.status()).thenReturn(404);
    when(awesomeApiClient.last("USD-XYZ")).thenThrow(notFound);

    mockMvc.perform(get("/exchange/USD/XYZ")).andExpect(status().isNotFound());
    mockMvc.perform(get("/exchange/USD/XYZ")).andExpect(status().isNotFound());

    verify(awesomeApiClient, times(2)).last("USD-XYZ");
  }

  @Test
  void moedasIguaisDa400() throws Exception {
    mockMvc.perform(get("/exchange/BRL/BRL")).andExpect(status().isBadRequest());
  }

  @Test
  void parInexistenteDa404() throws Exception {
    FeignException notFound = mock(FeignException.class);
    when(notFound.status()).thenReturn(404);
    when(awesomeApiClient.last("USD-XYZ")).thenThrow(notFound);

    mockMvc.perform(get("/exchange/USD/XYZ")).andExpect(status().isNotFound());
  }

  @Test
  void apiExternaForaDoArDa503() throws Exception {
    FeignException timeout = mock(FeignException.class);
    when(timeout.status()).thenReturn(-1);
    when(awesomeApiClient.last("USD-BRL")).thenThrow(timeout);

    mockMvc.perform(get("/exchange/USD/BRL")).andExpect(status().isServiceUnavailable());
  }

}
