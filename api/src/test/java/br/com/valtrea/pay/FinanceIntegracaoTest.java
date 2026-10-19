package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.valtrea.pay.finance.EnvioFinanceRepository;

class FinanceIntegracaoTest extends IntegracaoBase {

	record Status(boolean disponivel, boolean conectado) {
	}

	@Autowired
	EnvioFinanceRepository envios;

	@Test
	void pagamentosESaldoVaoParaOFinance() {
		String ana = cadastrarConfirmado("Ana Finance", emailUnico("ana-fin"));
		String bruno = cadastrarConfirmado("Bruno Finance", emailUnico("bruno-fin"));
		UUID idBruno = perfil(bruno).id();
		String codigo = novoCodigo();

		// Código que o Finance não reconhece é recusado na hora.
		assertThat(conectar(ana, "vfp_nao-existe")).isEqualTo(400);
		assertThat(conectar(ana, "qualquer-coisa")).isEqualTo(400);
		assertThat(status(ana).conectado()).isFalse();

		assertThat(conectar(ana, codigo)).isEqualTo(200);
		assertThat(status(ana).conectado()).isTrue();
		// Ao conectar, o Finance já recebe o saldo atual.
		assertThat(finance.de(codigo)).anyMatch(r -> r.rota().endsWith("/balance") && r.corpo().contains("\"balance\":0"));

		depositar(ana, "50.00");
		assertThat(transferir(ana, idBruno, "12.34", null).getStatusCode().value()).isEqualTo(201);

		esperar(() -> finance.de(codigo).stream().anyMatch(r -> r.rota().endsWith("/transactions")));
		var lancamentos = finance.de(codigo).stream().filter(r -> r.rota().endsWith("/transactions")).toList();
		// Só a transferência vira lançamento; o depósito não é um ganho.
		assertThat(lancamentos).hasSize(1);
		assertThat(lancamentos.getFirst().corpo()).contains("\"type\":\"EXPENSE\"", "\"amount\":12.34",
				":SAIDA\"", "Valtrea Pay · para Bruno Finance");
		esperar(() -> finance.de(codigo).stream().anyMatch(r -> r.corpo().contains("\"balance\":37.66")));
	}

	@Test
	void quemRecebeTambemVeOLancamentoSeEstiverConectado() {
		String carla = cadastrarConfirmado("Carla Finance", emailUnico("carla-fin"));
		String davi = cadastrarConfirmado("Davi Finance", emailUnico("davi-fin"));
		String codigoDavi = novoCodigo();
		assertThat(conectar(davi, codigoDavi)).isEqualTo(200);

		depositar(carla, "20.00");
		transferir(carla, perfil(davi).id(), "5.00", null);

		esperar(() -> finance.de(codigoDavi).stream().anyMatch(r -> r.corpo().contains("\"type\":\"EARNING\"")));
		assertThat(finance.de(codigoDavi)).anyMatch(r -> r.corpo().contains("Valtrea Pay · de Carla Finance"));
	}

	@Test
	void financeForaDoArNaoAtrapalhaOPagamentoEOEnvioFicaNaFila() {
		String eva = cadastrarConfirmado("Eva Finance", emailUnico("eva-fin"));
		String fabio = cadastrarConfirmado("Fabio Finance", emailUnico("fabio-fin"));
		String codigo = novoCodigo();
		assertThat(conectar(eva, codigo)).isEqualTo(200);
		UUID idEva = perfil(eva).id();
		depositar(eva, "10.00");
		esperar(() -> envios.findByUsuarioIdOrderByCriadoEm(idEva).stream().allMatch(e -> e.getEnviadoEm() != null));

		finance.foraDoAr(true);
		try {
			assertThat(transferir(eva, perfil(fabio).id(), "3.00", null).getStatusCode().value()).isEqualTo(201);
			// O envio continua pendente (vai ser tentado de novo mais tarde), sem perder o pagamento.
			esperar(() -> envios.findByUsuarioIdOrderByCriadoEm(idEva).stream()
					.anyMatch(e -> e.getEnviadoEm() == null));
			assertThat(perfil(eva).saldo()).isEqualByComparingTo("7.00");
		} finally {
			finance.foraDoAr(false);
		}
	}

	@Test
	void codigoRevogadoNoFinanceDesconectaAqui() {
		String gil = cadastrarConfirmado("Gil Finance", emailUnico("gil-fin"));
		String codigo = novoCodigo();
		assertThat(conectar(gil, codigo)).isEqualTo(200);

		finance.revogar(codigo);
		depositar(gil, "5.00");

		esperar(() -> !status(gil).conectado());
	}

	@Test
	void desconectarParaOsEnvios() {
		String hugo = cadastrarConfirmado("Hugo Finance", emailUnico("hugo-fin"));
		String codigo = novoCodigo();
		assertThat(conectar(hugo, codigo)).isEqualTo(200);
		assertThat(http.delete().uri("/api/v1/integracoes/finance").header("Authorization", "Bearer " + hugo)
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(200);

		int antes = finance.de(codigo).size();
		depositar(hugo, "5.00");
		dormir(Duration.ofMillis(800));
		assertThat(finance.de(codigo)).hasSize(antes);
	}

	private String novoCodigo() {
		String codigo = "vfp_" + UUID.randomUUID();
		finance.liberar(codigo);
		return codigo;
	}

	private int conectar(String token, String codigo) {
		return http.put().uri("/api/v1/integracoes/finance").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("codigo", codigo)).retrieve().toBodilessEntity()
				.getStatusCode().value();
	}

	private Status status(String token) {
		return http.get().uri("/api/v1/integracoes/finance").header("Authorization", "Bearer " + token).retrieve()
				.body(Status.class);
	}

	/** O envio acontece em segundo plano: espera até a condição valer (no máximo 5 segundos). */
	private static void esperar(BooleanSupplier condicao) {
		Instant limite = Instant.now().plusSeconds(5);
		while (!condicao.getAsBoolean()) {
			assertThat(Instant.now()).as("a condição não foi atendida em 5 segundos").isBefore(limite);
			dormir(Duration.ofMillis(100));
		}
	}

	private static void dormir(Duration tempo) {
		try {
			Thread.sleep(tempo);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
