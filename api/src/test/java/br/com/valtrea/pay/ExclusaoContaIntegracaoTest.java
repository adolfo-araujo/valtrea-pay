package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ExclusaoContaIntegracaoTest extends IntegracaoBase {

	record Contato(UUID id, String nome) {
	}

	record Transacao(String direcao, Contraparte contraparte) {
	}

	record Contraparte(String nome) {
	}

	record Pagina(List<Transacao> itens) {
	}

	@Test
	void excluirApagaOsDadosMasMantemOHistoricoDeQuemRecebeu() {
		String sufixo = UUID.randomUUID().toString().substring(0, 6);
		String emailMaria = emailUnico("maria");
		Tokens maria = cadastrar("Maria Saída " + sufixo, emailMaria);
		confirmarEmail(maria.accessToken(), emails.ultimoCodigo(emailMaria));
		String nina = cadastrarConfirmado("Nina Fica", emailUnico("nina"));
		UUID idNina = perfil(nina).id();
		UUID idMaria = perfil(maria.accessToken()).id();
		depositar(maria.accessToken(), "10.00");
		transferir(maria.accessToken(), idNina, "4.00", null);

		// Senha errada não exclui.
		assertThat(excluir(maria.accessToken(), "senha-errada").getStatusCode().value()).isEqualTo(403);

		// No sandbox, dá para excluir mesmo com saldo (o dinheiro é fictício).
		assertThat(excluir(maria.accessToken(), "senha-forte-123").getStatusCode().value()).isEqualTo(204);
		assertThat(emails.para(emailMaria).getLast().getSubject()).contains("excluída");

		// Não entra mais, a sessão caiu e nem o token de acesso ainda válido consegue usar a conta.
		assertThat(login(emailMaria, "senha-forte-123").getStatusCode().value()).isEqualTo(401);
		assertThat(post("/api/v1/auth/renovar", null, Map.of("refreshToken", maria.refreshToken())).getStatusCode()
				.value()).isEqualTo(401);
		assertThat(http.get().uri("/api/v1/eu").header("Authorization", "Bearer " + maria.accessToken()).retrieve()
				.toBodilessEntity().getStatusCode().value()).isEqualTo(404);
		assertThat(transferir(maria.accessToken(), idNina, "1.00", null).getStatusCode().value()).isEqualTo(404);

		// Some da busca e ninguém consegue pagar a ela.
		List<Contato> busca = http.get().uri("/api/v1/contatos?busca={t}", "maria saida " + sufixo)
				.header("Authorization", "Bearer " + nina).retrieve().body(new ParameterizedTypeReference<>() {
				});
		assertThat(busca).isEmpty();
		assertThat(transferir(nina, idMaria, "1.00", null).getStatusCode().value()).isEqualTo(404);

		// O extrato da Nina continua com o pagamento recebido, sem o nome de quem excluiu.
		Pagina extrato = http.get().uri("/api/v1/transacoes").header("Authorization", "Bearer " + nina).retrieve()
				.body(Pagina.class);
		assertThat(extrato.itens()).hasSize(1);
		assertThat(extrato.itens().getFirst().contraparte().nome()).isEqualTo("Conta excluída");

		// O e-mail pode ser usado de novo num cadastro novo.
		assertThat(cadastrar("Maria Nova", emailMaria).accessToken()).isNotBlank();
	}

	private ResponseEntity<Void> excluir(String token, String senha) {
		return http.method(org.springframework.http.HttpMethod.DELETE).uri("/api/v1/eu")
				.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("senha", senha)).retrieve().toBodilessEntity();
	}

}
