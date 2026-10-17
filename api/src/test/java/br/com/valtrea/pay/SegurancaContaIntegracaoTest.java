package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class SegurancaContaIntegracaoTest extends IntegracaoBase {

	@Test
	void soEnviaPagamentoDepoisDeConfirmarOEmail() {
		String email = emailUnico("fabi");
		Tokens fabi = cadastrar("Fabi Nunes", email);
		UUID destino = perfil(cadastrarConfirmado("Gil Prado", emailUnico("gil"))).id();
		depositar(fabi.accessToken(), "20.00");

		assertThat(perfil(fabi.accessToken()).emailConfirmado()).isFalse();
		assertThat(emails.para(email)).hasSize(1);
		assertThat(transferir(fabi.accessToken(), destino, "5.00", null).getStatusCode().value()).isEqualTo(403);

		String codigo = emails.ultimoCodigo(email);
		String errado = codigo.equals("000000") ? "111111" : "000000";
		assertThat(confirmarEmail(fabi.accessToken(), errado).getStatusCode().value()).isEqualTo(400);
		assertThat(confirmarEmail(fabi.accessToken(), codigo).getStatusCode().value()).isEqualTo(204);

		assertThat(perfil(fabi.accessToken()).emailConfirmado()).isTrue();
		assertThat(transferir(fabi.accessToken(), destino, "5.00", null).getStatusCode().value()).isEqualTo(201);
	}

	@Test
	void codigoDeConfirmacaoAceitaPoucasTentativas() {
		String email = emailUnico("hugo");
		String token = cadastrar("Hugo Reis", email).accessToken();
		String codigo = emails.ultimoCodigo(email);
		String errado = codigo.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			confirmarEmail(token, errado);
		}
		// Esgotou as tentativas: nem o código certo vale mais.
		assertThat(confirmarEmail(token, codigo).getStatusCode().value()).isEqualTo(400);

		// Reenviar logo em seguida é barrado; o limite é de um código por minuto.
		assertThat(post("/api/v1/auth/reenviar-confirmacao", token, Map.of()).getStatusCode().value()).isEqualTo(429);
	}

	@Test
	void renovarTrocaOsTokensECadaTokenDeSessaoValeUmaVez() {
		Tokens inicial = cadastrar("Iara Costa", emailUnico("iara"));

		// Um token de acesso inválido no cabeçalho não atrapalha a renovação.
		ResponseEntity<Tokens> renovado = http.post().uri("/api/v1/auth/renovar")
				.header("Authorization", "Bearer token-vencido").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("refreshToken", inicial.refreshToken())).retrieve().toEntity(Tokens.class);
		assertThat(renovado.getStatusCode().value()).isEqualTo(200);
		Tokens novo = renovado.getBody();
		assertThat(novo.refreshToken()).isNotEqualTo(inicial.refreshToken());
		assertThat(perfil(novo.accessToken()).nome()).isEqualTo("Iara Costa");

		// Reusar o token antigo indica cópia: derruba todas as sessões, inclusive a nova.
		assertThat(renovar(inicial.refreshToken())).isEqualTo(401);
		assertThat(renovar(novo.refreshToken())).isEqualTo(401);
	}

	@Test
	void sairEncerraASessao() {
		Tokens tokens = cadastrar("Joana Lopes", emailUnico("joana"));
		assertThat(post("/api/v1/auth/sair", null, Map.of("refreshToken", tokens.refreshToken())).getStatusCode()
				.value()).isEqualTo(204);
		assertThat(renovar(tokens.refreshToken())).isEqualTo(401);
	}

	@Test
	void recuperarSenhaComCodigoPorEmail() {
		String email = emailUnico("kaio");
		Tokens antes = cadastrar("Kaio Ramos", email);

		// E-mail sem conta: mesma resposta, nada é enviado.
		String inexistente = emailUnico("ninguem");
		assertThat(post("/api/v1/auth/esqueci-senha", null, Map.of("email", inexistente)).getStatusCode().value())
				.isEqualTo(202);
		assertThat(emails.para(inexistente)).isEmpty();

		assertThat(post("/api/v1/auth/esqueci-senha", null, Map.of("email", email)).getStatusCode().value())
				.isEqualTo(202);
		String codigo = emails.ultimoCodigo(email);

		assertThat(post("/api/v1/auth/redefinir-senha", null,
				Map.of("email", email, "codigo", codigo, "novaSenha", "nova-senha-456")).getStatusCode().value())
				.isEqualTo(204);

		assertThat(login(email, "senha-forte-123").getStatusCode().value()).isEqualTo(401);
		Tokens depois = login(email, "nova-senha-456").getBody();
		assertThat(depois.accessToken()).isNotBlank();
		// Trocar a senha desconecta os aparelhos e confirma o e-mail (a pessoa recebeu o código nele).
		assertThat(renovar(antes.refreshToken())).isEqualTo(401);
		assertThat(perfil(depois.accessToken()).emailConfirmado()).isTrue();
		assertThat(emails.para(email).getLast().getSubject()).contains("senha").contains("alterada");

		// O código só vale uma vez.
		assertThat(post("/api/v1/auth/redefinir-senha", null,
				Map.of("email", email, "codigo", codigo, "novaSenha", "outra-senha-789")).getStatusCode().value())
				.isEqualTo(400);
	}

	@Test
	void bloqueioDoLoginAvisaPorEmail() {
		String email = emailUnico("lia");
		cadastrar("Lia Mota", email);
		for (int i = 0; i < 5; i++) {
			assertThat(login(email, "senha-errada").getStatusCode().value()).isEqualTo(401);
		}
		assertThat(login(email, "senha-forte-123").getStatusCode().value()).isEqualTo(429);
		assertThat(emails.para(email).getLast().getSubject()).contains("Tentativas de acesso");
	}

	private int renovar(String refreshToken) {
		return post("/api/v1/auth/renovar", null, Map.of("refreshToken", refreshToken)).getStatusCode().value();
	}

}
