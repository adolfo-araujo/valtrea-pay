package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sobe a API com um PostgreSQL real e percorre o fluxo da carteira pela HTTP, como o app faria. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"valtrea.jwt.segredo=segredo-de-teste-com-pelo-menos-32-caracteres", "valtrea.sandbox.habilitado=true" })
class CarteiraIntegracaoTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	@Value("${local.server.port}")
	int porta;

	RestClient http;

	record Token(String accessToken) {
	}

	record Perfil(UUID id, String nome, BigDecimal saldo) {
	}

	record Contato(UUID id, String nome, String emailMascarado) {
	}

	record Transacao(UUID id, String tipo, String direcao, BigDecimal valor) {
	}

	record Pagina(List<Transacao> itens, long totalItens) {
	}

	@BeforeEach
	void configurar() {
		http = RestClient.builder().baseUrl("http://localhost:" + porta)
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				}).build();
	}

	@Test
	void fluxoCompletoDaCarteira() {
		String sufixo = UUID.randomUUID().toString().substring(0, 8);
		String ana = cadastrar("Ana Souza", "ana-" + sufixo + "@exemplo.com", cpfValido(1));
		String bruno = cadastrar("Bruno Lima", "bruno-" + sufixo + "@exemplo.com", cpfValido(2));
		Perfil perfilBruno = perfil(bruno);

		// Sem saldo, a transferência é recusada.
		assertThat(transferir(ana, perfilBruno.id(), "10.00", null).getStatusCode().value()).isEqualTo(422);

		depositar(ana, "100.00");
		assertThat(perfil(ana).saldo()).isEqualByComparingTo("100.00");

		// A busca encontra o Bruno pelo nome e não expõe o e-mail inteiro.
		List<Contato> contatos = http.get().uri("/api/v1/contatos?busca=bruno").header("Authorization", "Bearer " + ana)
				.retrieve().body(new ParameterizedTypeReference<>() {
				});
		assertThat(contatos).extracting(Contato::id).contains(perfilBruno.id());
		assertThat(contatos.getFirst().emailMascarado()).contains("***").doesNotContain("bruno-" + sufixo);

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
		String sufixo = UUID.randomUUID().toString().substring(0, 8);
		String carla = cadastrar("Carla Dias", "carla-" + sufixo + "@exemplo.com", cpfValido(3));
		String davi = cadastrar("Davi Melo", "davi-" + sufixo + "@exemplo.com", cpfValido(4));
		UUID daviId = perfil(davi).id();
		depositar(carla, "50.00");

		// 20 pedidos de R$ 10 ao mesmo tempo com R$ 50 de saldo: só 5 podem passar.
		List<Callable<Integer>> pedidos = java.util.stream.IntStream.range(0, 20)
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
	void rotasProtegidasExigemToken() {
		assertThat(http.get().uri("/api/v1/eu").retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void loginBloqueiaDepoisDeVariasSenhasErradas() {
		String email = "eva-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
		cadastrar("Eva Rocha", email, cpfValido(5));
		for (int i = 0; i < 5; i++) {
			assertThat(login(email, "senha-errada").getStatusCode().value()).isEqualTo(401);
		}
		assertThat(login(email, "senha-forte-123").getStatusCode().value()).isEqualTo(429);
	}

	private String cadastrar(String nome, String email, String cpf) {
		return http.post().uri("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("nome", nome, "email", email, "cpf", cpf, "telefone", "(21) 99999-0000", "senha",
						"senha-forte-123"))
				.retrieve().body(Token.class).accessToken();
	}

	private ResponseEntity<Void> login(String email, String senha) {
		return http.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", email, "senha", senha)).retrieve().toBodilessEntity();
	}

	private Perfil perfil(String token) {
		return http.get().uri("/api/v1/eu").header("Authorization", "Bearer " + token).retrieve().body(Perfil.class);
	}

	private void depositar(String token, String valor) {
		http.post().uri("/api/v1/depositos").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("valor", new BigDecimal(valor))).retrieve()
				.toBodilessEntity();
	}

	private ResponseEntity<Void> transferir(String token, UUID destino, String valor, String chave) {
		var pedido = http.post().uri("/api/v1/transferencias").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("destinoId", destino, "valor", new BigDecimal(valor)));
		if (chave != null) {
			pedido = pedido.header("Idempotency-Key", chave);
		}
		return pedido.retrieve().toBodilessEntity();
	}

	/** Gera um CPF válido a partir de uma semente, para cada teste ter o seu. */
	private static String cpfValido(int semente) {
		long base = 100_000_000L + Math.abs((long) UUID.randomUUID().hashCode() % 800_000_000L) + semente;
		String nove = String.valueOf(base).substring(0, 9);
		int d1 = digito(nove, 10);
		int d2 = digito(nove + d1, 11);
		return nove + d1 + d2;
	}

	private static int digito(String parcial, int pesoInicial) {
		int soma = 0;
		for (int i = 0; i < parcial.length(); i++) {
			soma += (parcial.charAt(i) - '0') * (pesoInicial - i);
		}
		int resto = (soma * 10) % 11;
		return resto == 10 ? 0 : resto;
	}

}
