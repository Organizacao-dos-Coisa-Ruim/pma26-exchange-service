# pma26-exchange-service

Implementação do serviço **Exchange**: cotação entre moedas para o usuário transacionar em outra moeda.
O contrato (rotas + DTOs) está na interface [`pma26-exchange`](https://github.com/Organizacao-dos-Coisa-Ruim/pma26-exchange).

Não tem banco: a cotação vem da [AwesomeAPI](https://docs.awesomeapi.com.br/api-de-moedas), que é grátis e não pede chave.

## Endpoints

| Método | Rota | Resposta |
|--------|------|----------|
| `GET` | `/exchange/{from}/{to}` | `200` + `{sell, buy, date, id-account}` · `400` código inválido ou moedas iguais · `404` par inexistente · `503` AwesomeAPI fora do ar ou lenta |
| `GET` | `/exchange/health-check` | `200` |

```bash
# pelo gateway (precisa de token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/exchange/USD/BRL
# {"sell":5.668,"buy":5.6666,"date":"2026-11-13 18:30:13","id-account":"..."}
```

`sell` é o preço de venda (`ask` na AwesomeAPI) e `buy` é o preço de compra (`bid`).

## Como funciona

```
ExchangeResource ──► ExchangeService ──► RateProvider ──► AwesomeApiClient (Feign) ──► AwesomeAPI
 (lê id-account)     (valida moedas)     (traduz erros)     GET /json/last/USD-BRL
```

| Classe | Papel |
|--------|-------|
| `ExchangeResource` | implementa `ExchangeController`; lê o header `id-account` que o gateway coloca |
| `ExchangeService` | valida os códigos (3 letras, maiúsculas ou minúsculas, origem ≠ destino) |
| `RateProvider` | chama a API externa: 404 vira `404`, timeout ou 5xx vira `503` |
| `AwesomeApiClient` / `AwesomeQuote` | cliente Feign e formato da resposta da AwesomeAPI |
| `Rate` | cotação do par, igual para todos os usuários (é o que vai para o cache Redis) |
| `QuoteParser` | `AwesomeQuote → Rate → QuoteOut` |

**Timeouts:** 2 s para conectar e 3 s para ler (`application.yaml`). Se a AwesomeAPI travar, o serviço devolve `503` rápido em vez de prender a thread.

**Por que `RateProvider` é uma classe separada?** É onde entra o `@Cacheable` do Redis (issue #76). O cache do Spring funciona por proxy e só intercepta chamadas vindas de outra classe.

## Configuração

| Variável | Padrão | Para quê |
|----------|--------|----------|
| `EXCHANGE_API_URL` | `https://economia.awesomeapi.com.br` | URL da API de cotações (troque para um mock em testes de carga) |

## Rodando

```bash
# interface antes (uma vez)
cd ../exchange && ./mvnw clean install

./mvnw spring-boot:run      # ou
./mvnw clean package && docker build -t exchange .
```

No sistema inteiro: `docker compose -f docker/compose.yml up --build` no `pma26`.

## Testes

```bash
./mvnw verify
```

- `ExchangeServiceTest`: validação das moedas, com o `RateProvider` mockado.
- `RateProviderTest`: conversão e tradução de erros (404, timeout, 5xx, valor inválido), com a API externa mockada.
- `ExchangeResourceIntegrationTest`: a aplicação inteira com MockMvc e a API externa mockada (`@MockitoBean`), sem acessar a internet.
