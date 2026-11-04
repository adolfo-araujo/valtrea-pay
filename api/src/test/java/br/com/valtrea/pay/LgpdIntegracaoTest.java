package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.valtrea.pay.retencao.RetencaoService;
import br.com.valtrea.pay.usuario.Termos;

/** Celular opcional, senha forte, "Baixar meus dados", aceite dos Termos e rotina de retenção. */
class LgpdIntegracaoTest extends IntegracaoBase {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	RetencaoService retencao;

	record PerfilCompleto(UUID id, String telefone, boolean termosAceitos) {
	}

	private PerfilCompleto perfilCompleto(String token) {
		return http.get().uri("/api/v1/eu").header("Authorization", "Bearer " + token).retrieve()
				.body(PerfilCompleto.class);
	}

	private ResponseEntity<Void> cadastro(Map<String, Object> corpo) {
		return http.post().uri("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve()
				.toBodilessEntity();
	}

	private ResponseEntity<Void> excluir(String token) {
		return http.method(HttpMethod.DELETE).uri("/api/v1/eu").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("senha", "senha-forte-123")).retrieve()
				.toBodilessEntity();
	}

	private boolean existe(UUID usuarioId) {
		return jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE id = ?", Integer.class, usuarioId) == 1;
	}

	private int transacoesDa(UUID usuarioId) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM transacoes t JOIN contas c ON c.id = t.conta_origem_id OR c.id = t.conta_destino_id
				 WHERE c.usuario_id = ?""", Integer.class, usuarioId);
	}

	@Test
	void celularEOpcionalESenhaPrecisaDeLetrasENumeros() {
		var semCelular = cadastro(Map.of("nome", "Sem Celular", "email", emailUnico("semcel"), "cpf", cpfValido(),
				"senha", "senha-forte-123"));
		assertThat(semCelular.getStatusCode().value()).isEqualTo(201);

		var celularVazio = http.post().uri("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("nome", "Celular Vazio", "email", emailUnico("vazio"), "cpf", cpfValido(), "telefone", "",
						"senha", "senha-forte-123"))
				.retrieve().body(Tokens.class);
		assertThat(perfilCompleto(celularVazio.accessToken()).telefone()).isEmpty();

		var semNumero = cadastro(Map.of("nome", "Senha Fraca", "email", emailUnico("fraca"), "cpf", cpfValido(),
				"senha", "somenteletras"));
		assertThat(semNumero.getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void baixarMeusDadosTrazTudoMenosSenhaECodigos() {
		String email = emailUnico("olga");
		String olga = cadastrarConfirmado("Olga Dados", email);
		UUID destino = perfil(cadastrarConfirmado("Paulo Recebe", emailUnico("paulo"))).id();
		depositar(olga, "30.00");
		transferir(olga, destino, "7.50", null);

		ResponseEntity<String> resposta = http.get().uri("/api/v1/eu/dados").header("Authorization", "Bearer " + olga)
				.retrieve().toEntity(String.class);
		assertThat(resposta.getStatusCode().value()).isEqualTo(200);
		assertThat(resposta.getHeaders().getFirst("Content-Disposition")).contains("valtrea-pay-meus-dados-");
		assertThat(resposta.getBody()).contains(email).contains("\"cpf\"").contains("Paulo Recebe").contains("7.5")
				.contains("\"termosVersao\":\"" + Termos.VERSAO + "\"");
		assertThat(resposta.getBody()).doesNotContain("senha").doesNotContain("$2a$").doesNotContain("token");

		assertThat(http.get().uri("/api/v1/eu/dados").retrieve().toBodilessEntity().getStatusCode().value())
				.isEqualTo(401);
	}

	@Test
	void contaNovaJaAceitaOsTermosEAntigaPrecisaAceitar() {
		String token = cadastrar("Quela Termos", emailUnico("quela")).accessToken();
		PerfilCompleto perfil = perfilCompleto(token);
		assertThat(perfil.termosAceitos()).isTrue();

		// Conta criada antes dos Termos: o app mostra o aviso até a pessoa aceitar.
		jdbc.update("UPDATE usuarios SET termos_versao = NULL, termos_aceitos_em = NULL WHERE id = ?", perfil.id());
		assertThat(perfilCompleto(token).termosAceitos()).isFalse();
		assertThat(post("/api/v1/eu/termos", token, Map.of("versao", "2000-01-01")).getStatusCode().value())
				.isEqualTo(400);
		assertThat(post("/api/v1/eu/termos", token, Map.of("versao", Termos.VERSAO)).getStatusCode().value())
				.isEqualTo(200);
		assertThat(perfilCompleto(token).termosAceitos()).isTrue();
		assertThat(jdbc.queryForObject("SELECT termos_aceitos_em IS NOT NULL FROM usuarios WHERE id = ?",
				Boolean.class, perfil.id())).isTrue();
	}

	@Test
	void retencaoApagaSoOQueNaoPrecisaMaisSerGuardado() {
		// Cadastro antigo nunca confirmado e sem transações: apagado.
		UUID parado = perfil(cadastrar("Rui Parado", emailUnico("rui")).accessToken()).id();
		// Cadastro antigo nunca confirmado, mas com depósito: fica.
		String comDepositoToken = cadastrar("Sara Deposito", emailUnico("sara")).accessToken();
		UUID comDeposito = perfil(comDepositoToken).id();
		depositar(comDepositoToken, "5.00");
		// Cadastro recente nunca confirmado: fica.
		UUID recente = perfil(cadastrar("Tais Recente", emailUnico("tais")).accessToken()).id();
		jdbc.update("UPDATE usuarios SET criado_em = now() - interval '31 days' WHERE id IN (?, ?)", parado,
				comDeposito);

		// Uma pagou para a outra e depois excluiu a conta.
		String ursula = cadastrarConfirmado("Ursula Sai", emailUnico("ursula"));
		UUID idUrsula = perfil(ursula).id();
		String vera = cadastrarConfirmado("Vera Fica", emailUnico("vera"));
		UUID idVera = perfil(vera).id();
		depositar(ursula, "20.00");
		transferir(ursula, idVera, "3.00", null);
		assertThat(excluir(ursula).getStatusCode().value()).isEqualTo(204);

		retencao.executar(Instant.now());

		assertThat(existe(parado)).isFalse();
		assertThat(existe(comDeposito)).isTrue();
		assertThat(existe(recente)).isTrue();
		// O depósito de quem excluiu some; o pagamento continua no extrato de quem ainda tem conta.
		assertThat(transacoesDa(idUrsula)).isEqualTo(1);
		assertThat(transacoesDa(idVera)).isEqualTo(1);
		assertThat(existe(idUrsula)).isTrue();

		// Quando a outra pessoa também exclui, a transação e as duas contas excluídas são apagadas.
		assertThat(excluir(vera).getStatusCode().value()).isEqualTo(204);
		retencao.executar(Instant.now());
		assertThat(transacoesDa(idVera)).isZero();
		assertThat(existe(idUrsula)).isFalse();
		assertThat(existe(idVera)).isFalse();
	}

}
