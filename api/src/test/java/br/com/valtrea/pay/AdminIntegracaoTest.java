package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Painel de administração: só para administradores e só com números agregados. */
class AdminIntegracaoTest extends IntegracaoBase {

	@Autowired
	JdbcTemplate jdbc;

	record PerfilAdmin(UUID id, boolean admin) {
	}

	private PerfilAdmin perfilAdmin(String token) {
		return http.get().uri("/api/v1/eu").header("Authorization", "Bearer " + token).retrieve()
				.body(PerfilAdmin.class);
	}

	private ResponseEntity<String> metricas(String token) {
		return http.get().uri("/api/v1/admin/metricas").header("Authorization", "Bearer " + token).retrieve()
				.toEntity(String.class);
	}

	@Test
	void pessoaComumNaoVeOPainel() {
		String comum = cadastrarConfirmado("Pessoa Comum", emailUnico("comum"));
		assertThat(perfilAdmin(comum).admin()).isFalse();
		assertThat(metricas(comum).getStatusCode().value()).isEqualTo(403);
		assertThat(http.get().uri("/api/v1/admin/metricas").retrieve().toBodilessEntity().getStatusCode().value())
				.isEqualTo(401);
	}

	@Test
	void emailDaListaVeOPainelDepoisDeConfirmar() {
		String token = cadastrar("Chefe", "chefe-pay@exemplo.com").accessToken();
		assertThat(perfilAdmin(token).admin()).as("sem e-mail confirmado ainda não é admin").isFalse();
		assertThat(confirmarEmail(token, emails.ultimoCodigo("chefe-pay@exemplo.com")).getStatusCode().value())
				.isEqualTo(204);
		assertThat(perfilAdmin(token).admin()).isTrue();
		assertThat(metricas(token).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void papelAdministradorMostraNumerosAgregadosSemDadosPessoais() {
		String emailAdmin = emailUnico("admin");
		String admin = cadastrarConfirmado("Ana Admin", emailAdmin);
		jdbc.update("UPDATE usuarios SET papel = 'ADMINISTRADOR' WHERE email = ?", emailAdmin);

		String pagadora = cadastrarConfirmado("Bia Pagadora", emailUnico("bia"));
		UUID recebedor = perfil(cadastrarConfirmado("Caio Recebe", emailUnico("caio"))).id();
		depositar(pagadora, "50.00");
		transferir(pagadora, recebedor, "12.34", null);

		ResponseEntity<String> resposta = metricas(admin);
		assertThat(resposta.getStatusCode().value()).isEqualTo(200);
		String corpo = resposta.getBody();
		assertThat(corpo).contains("\"contas\"", "\"atividade\"", "\"finance\"", "\"cadastrosPorSemana\"");
		assertThat(corpo).doesNotContain("Bia", "Caio", "@exemplo.com", "cpf");

		var m = http.get().uri("/api/v1/admin/metricas").header("Authorization", "Bearer " + admin).retrieve()
				.body(MetricasLidas.class);
		assertThat(m.contas().get("ativas")).isGreaterThanOrEqualTo(3);
		assertThat(m.contas().get("confirmadas")).isGreaterThanOrEqualTo(3);
		assertThat(m.atividade().get("pagantes7Dias")).isGreaterThanOrEqualTo(1);
		assertThat(m.atividade().get("transacoes24Horas")).isGreaterThanOrEqualTo(2);
		assertThat(m.atividade().get("comSessaoAtiva")).isGreaterThanOrEqualTo(3);
		assertThat(m.cadastrosPorSemana()).hasSize(8);
		assertThat(m.cadastrosPorSemana().getLast().get("cadastros")).isGreaterThanOrEqualTo(3);
	}

	record MetricasLidas(Map<String, Number> contas, Map<String, Number> atividade, Map<String, Number> finance,
			java.util.List<Map<String, Object>> cadastrosPorSemana) {
	}

}
