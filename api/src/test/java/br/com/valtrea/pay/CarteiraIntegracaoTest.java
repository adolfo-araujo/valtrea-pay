package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

class CarteiraIntegracaoTest extends IntegracaoBase {

	record Contato(UUID id, String nome, String emailMascarado) {
	}

	record Transacao(UUID id, String tipo, String direcao) {
	}

	record Pagina(List<Transacao> itens, long totalItens) {
	}

	@Test
	void fluxoCompletoDaCarteira() {
		String emailBruno = emailUnico("bruno");
		String ana = cadastrarConfirmado("Ana Souza", emailUnico("ana"));
		String bruno = cadastrarConfirmado("Bruno Lima", emailBruno);
		Perfil perfilBruno = perfil(bruno);

		// Sem saldo, a transferência é recusada.
		assertThat(transferir(ana, perfilBruno.id(), "10.00", null).getStatusCode().value()).isEqualTo(422);

		depositar(ana, "100.00");
		assertThat(perfil(ana).saldo()).isEqualByComparingTo("100.00");

		// A busca encontra o Bruno pelo nome e não expõe o e-mail inteiro.
		List<Contato> contatos = http.get().uri("/api/v1/contatos?busca=bruno lima")
				.header("Authorization", "Bearer " + ana).retrieve().body(new ParameterizedTypeReference<>() {
				});
		assertThat(contatos).extracting(Contato::id).contains(perfilBruno.id());
		assertThat(contatos).extracting(Contato::emailMascarado).allMatch(e -> e.contains("***"))
				.doesNotContain(emailBruno);

		// Repetir a mesma chave de idempotência não cobra duas vezes.
		String chave = UUID.randomUUID().toString();
		assertThat(transferir(ana, perfilBruno.id(), "30.50", chave).getStatusCode().value()).isEqualTo(201);
		assertThat(transferir(ana, perfilBruno.id(), "30.50", chave).getStatusCode().value()).isEqualTo(201);
		assertThat(perfil(ana).saldo()).isEqualByComparingTo("69.50");
		assertThat(perfil(bruno).saldo()).isEqualByComparingTo("30.50");

		// A mesma chave com outro valor é recusada.
		assertThat(transferir(ana, perfilBruno.id(), "1.00", chave).getStatusCode().value()).isEqualTo(409);

		Pagina extratoBruno = http.get().uri("/api/v1/transacoes").header("Authorization", "Bearer " + bruno)
				.retrieve().body(Pagina.class);
		assertThat(extratoBruno.totalItens()).isEqualTo(1);
		assertThat(extratoBruno.itens().getFirst().direcao()).isEqualTo("ENTRADA");
	}

	@Test
	void transferenciasSimultaneasNuncaDeixamSaldoNegativo() throws Exception {
		String carla = cadastrarConfirmado("Carla Dias", emailUnico("carla"));
		String davi = cadastrarConfirmado("Davi Melo", emailUnico("davi"));
		UUID daviId = perfil(davi).id();
		depositar(carla, "50.00");

		// 20 pedidos de R$ 10 ao mesmo tempo com R$ 50 de saldo: só 5 podem passar.
		List<Callable<Integer>> pedidos = IntStream.range(0, 20)
				.<Callable<Integer>>mapToObj(i -> () -> transferir(carla, daviId, "10.00", null).getStatusCode().value())
				.toList();
		try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
			long aprovadas = 0;
			for (Future<Integer> f : executor.invokeAll(pedidos)) {
				if (f.get() == 201) {
					aprovadas++;
				}
			}
			assertThat(aprovadas).isEqualTo(5);
		}
		assertThat(perfil(carla).saldo()).isEqualByComparingTo("0.00");
		assertThat(perfil(davi).saldo()).isEqualByComparingTo("50.00");
	}

	@Test
	void qrCodeIdentificaQuemVaiReceber() {
		String emailMara = emailUnico("mara");
		String mara = cadastrarConfirmado("Mara Vidal", emailMara);
		String nico = cadastrarConfirmado("Nico Prates", emailUnico("nico"));
		UUID idMara = perfil(mara).id();

		Contato lido = http.get().uri("/api/v1/contatos/{id}", idMara).header("Authorization", "Bearer " + nico)
				.retrieve().body(Contato.class);
		assertThat(lido.nome()).isEqualTo("Mara Vidal");
		assertThat(lido.emailMascarado()).contains("***").isNotEqualTo(emailMara);

		// O próprio código e códigos inexistentes são recusados.
		assertThat(http.get().uri("/api/v1/contatos/{id}", idMara).header("Authorization", "Bearer " + mara)
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(400);
		assertThat(http.get().uri("/api/v1/contatos/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + nico)
				.retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void buscaIgnoraAcentosEMaiusculas() {
		String sufixo = UUID.randomUUID().toString().substring(0, 6);
		String procurado = cadastrarConfirmado("Zé Conceição Araújo " + sufixo, emailUnico("ze"));
		String quemBusca = cadastrarConfirmado("Ivo Busca", emailUnico("ivo"));
		UUID id = perfil(procurado).id();

		for (String termo : List.of("conceicao araujo " + sufixo, "CONCEIÇÃO ARAÚJO " + sufixo,"ZE CONCEICAO ARAUJO " + sufixo)) {
			List<Contato> achados = http.get().uri("/api/v1/contatos?busca={termo}", termo)
					.header("Authorization", "Bearer " + quemBusca).retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
			assertThat(achados).as("busca por \"%s\"", termo).extracting(Contato::id).containsExactly(id);
		}
	}

	@Test
	void saudeNaoDependeDoServidorDeEmail() {
		// Nos testes não há servidor SMTP. Se a verificação de e-mail estivesse ligada, a saúde viria DOWN (503),
		// como aconteceu no Render, onde as portas de SMTP são bloqueadas.
		var saude = http.get().uri("/actuator/health").retrieve().toEntity(String.class);
		assertThat(saude.getStatusCode().value()).isEqualTo(200);
		assertThat(saude.getBody()).contains("\"status\":\"UP\"");
	}

	@Test
	void trocaNomeETelefoneEABuscaAcompanha() {
		String sufixo = UUID.randomUUID().toString().substring(0, 6);
		String lia = cadastrarConfirmado("Lia Antiga " + sufixo, emailUnico("lia"));
		String outro = cadastrarConfirmado("Outro Busca", emailUnico("outro"));

		var resposta = http.patch().uri("/api/v1/eu").header("Authorization", "Bearer " + lia)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body(java.util.Map.of("nome", "Lia Conceição " + sufixo, "telefone", "(21) 98888-7777"))
				.retrieve().toEntity(Perfil.class);
		assertThat(resposta.getStatusCode().value()).isEqualTo(200);
		assertThat(resposta.getBody().nome()).isEqualTo("Lia Conceição " + sufixo);

		// A busca encontra pelo nome novo (sem acento) e não pelo antigo.
		List<Contato> novo = http.get().uri("/api/v1/contatos?busca={t}", "lia conceicao " + sufixo)
				.header("Authorization", "Bearer " + outro).retrieve().body(new ParameterizedTypeReference<>() {
				});
		List<Contato> antigo = http.get().uri("/api/v1/contatos?busca={t}", "lia antiga " + sufixo)
				.header("Authorization", "Bearer " + outro).retrieve().body(new ParameterizedTypeReference<>() {
				});
		assertThat(novo).hasSize(1);
		assertThat(antigo).isEmpty();

		var invalido = http.patch().uri("/api/v1/eu").header("Authorization", "Bearer " + lia)
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body(java.util.Map.of("nome", " ", "telefone", "abc")).retrieve().toBodilessEntity();
		assertThat(invalido.getStatusCode().value()).isEqualTo(400);
	}

	@Test
	void paginasPublicasDePrivacidadeEExclusao() {
		var privacidade = http.get().uri("/privacidade").retrieve().toEntity(String.class);
		assertThat(privacidade.getStatusCode().value()).isEqualTo(200);
		assertThat(privacidade.getBody()).contains("Política de Privacidade", "LGPD");

		var exclusao = http.get().uri("/excluir-conta").retrieve().toEntity(String.class);
		assertThat(exclusao.getStatusCode().value()).isEqualTo(200);
		assertThat(exclusao.getBody()).contains("Perfil", "Excluir conta");
	}

	@Test
	void raizLevaParaADocumentacao() {
		var resposta = http.get().uri("/").retrieve().toBodilessEntity();
		assertThat(resposta.getStatusCode().is3xxRedirection()).isTrue();
		assertThat(resposta.getHeaders().getLocation().getPath()).isEqualTo("/swagger-ui.html");
	}

	@Test
	void rotasProtegidasExigemToken() {
		assertThat(http.get().uri("/api/v1/eu").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(401);
	}

}
