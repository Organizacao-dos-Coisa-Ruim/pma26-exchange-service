package pma.exchange;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Implementa o contrato ExchangeController: as rotas vêm das anotações da interface.
 * Só repassa para o ExchangeService e monta a resposta HTTP.
 *
 * O id-account vem do header que o gateway coloca depois de validar o JWT. Ele é lido aqui
 * (e não declarado na interface) para quem chama via Feign, como o Order, não precisar enviá-lo.
 */
@RestController
@RequiredArgsConstructor
public class ExchangeResource implements ExchangeController {

  static final String HEADER_ID_ACCOUNT = "id-account";

  private final ExchangeService exchangeService;
  /** Proxy do Spring: aponta sempre para a requisição atual. */
  private final HttpServletRequest request;

  @Override
  public ResponseEntity<QuoteOut> quote(String from, String to) {
    return ResponseEntity.ok(exchangeService.quote(from, to, request.getHeader(HEADER_ID_ACCOUNT)));
  }

  @Override
  public ResponseEntity<Void> healthCheck() {
    return ResponseEntity.ok().build();
  }

}
