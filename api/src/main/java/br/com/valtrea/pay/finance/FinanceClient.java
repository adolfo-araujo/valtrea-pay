package br.com.valtrea.pay.finance;

import java.time.Duration;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import br.com.valtrea.pay.config.ValtreaProperties;

/** Fala com as rotas de integração da API do Valtrea Finance, usando o código de conexão da pessoa. */
@Component
public class FinanceClient {

	public sealed interface Resultado {
		record Ok() implements Resultado {
		}

		/** O código foi revogado no Finance (gerou outro ou desconectou). */
		record NaoAutorizado() implements Resultado {
		}

		/** Vale tentar de novo depois (Finance fora do ar, erro 5xx, rede). */
		record Temporario(String motivo) implements Resultado {
		}

		/** O Finance recusou os dados; repetir não adianta. */
		record Recusado(String motivo) implements Resultado {
		}
	}

	private final RestClient http;

	public FinanceClient(ValtreaProperties propriedades) {
		var fabrica = new JdkClientHttpRequestFactory();
		fabrica.setReadTimeout(Duration.ofSeconds(10));
		this.http = RestClient.builder()
				.baseUrl(propriedades.finance().habilitada() ? propriedades.finance().url() : "http://desligado.invalid")
				.requestFactory(fabrica)
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				})
				.build();
	}

	public Resultado lancarTransacao(String token, String json) {
		return enviar("POST", "/integrations/pay/transactions", token, json);
	}

	public Resultado atualizarSaldo(String token, String json) {
		return enviar("PUT", "/integrations/pay/balance", token, json);
	}

	private Resultado enviar(String metodo, String rota, String token, String json) {
		try {
			var resposta = (metodo.equals("PUT") ? http.put() : http.post())
					.uri(rota)
					.header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON)
					.body(json)
					.retrieve()
					.toEntity(String.class);
			int status = resposta.getStatusCode().value();
			if (status < 300) {
				return new Resultado.Ok();
			}
			if (status == 401) {
				return new Resultado.NaoAutorizado();
			}
			if (status == 429 || status >= 500) {
				return new Resultado.Temporario("HTTP " + status);
			}
			return new Resultado.Recusado("HTTP " + status + ": " + resposta.getBody());
		} catch (ResourceAccessException e) {
			return new Resultado.Temporario("sem conexão: " + e.getMessage());
		}
	}

}
