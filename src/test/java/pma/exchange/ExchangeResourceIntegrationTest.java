package pma.exchange;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import feign.FeignException;

/** Sobe a aplicação inteira com o cliente da API externa mockado (os testes não acessam a internet). */
// métricas ficam desligadas em testes por padrão; religamos para testar o endpoint do Prometheus
@AutoConfigureMetrics
@SpringBootTest
@AutoConfigureMockMvc
class ExchangeResourceIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AwesomeApiClient awesomeApiClient;

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
