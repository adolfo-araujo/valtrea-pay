package br.com.valtrea.pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sobe a API com um PostgreSQL real e fala com ela por HTTP, como o app faria. O banco é um só para todas as classes
 * de teste (cada teste usa e-mails e CPFs próprios), e os e-mails ficam numa caixa falsa em vez de sair por SMTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"valtrea.jwt.segredo=segredo-de-teste-com-pelo-menos-32-caracteres", "valtrea.sandbox.habilitado=true",
		"valtrea.finance.chave-cifra=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"valtrea.finance.intervalo-envio-ms=200" })
@Import(IntegracaoBase.Emails.class)
abstract class IntegracaoBase {

	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

	/** Imita a API do Valtrea Finance, para testar a integração sem depender dela. */
	static final FinanceFalso finance = new FinanceFalso();

	static {
		postgres.start();
	}

	@DynamicPropertySource
	static void apontarParaFinanceFalso(DynamicPropertyRegistry registro) {
		registro.add("valtrea.finance.url", finance::url);
	}

	@TestConfiguration
	static class Emails {
		@Bean
		CaixaDeEmails caixaDeEmails() {
			return new CaixaDeEmails();
		}
	}

	/** Guarda os e-mails em memória. Substitui o JavaMailSender de verdade. */
	static class CaixaDeEmails extends JavaMailSenderImpl {
		final List<SimpleMailMessage> enviados = new CopyOnWriteArrayList<>();

		@Override
		public void send(SimpleMailMessage... mensagens) {
			enviados.addAll(List.of(mensagens));
		}

		List<SimpleMailMessage> para(String email) {
			return enviados.stream().filter(m -> List.of(m.getTo()).contains(email)).toList();
		}

		String ultimoCodigo(String email) {
			List<SimpleMailMessage> recebidos = para(email);
			assertThat(recebidos).as("e-mails para " + email).isNotEmpty();
			Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(recebidos.getLast().getText());
			assertThat(m.find()).isTrue();
			return m.group(1);
		}
	}

	record Tokens(String accessToken, String refreshToken, Instant expiraEm) {
	}

	record Perfil(UUID id, String nome, BigDecimal saldo, boolean emailConfirmado) {
	}

	@Value("${local.server.port}")
	int porta;

	@Autowired
	CaixaDeEmails emails;

	RestClient http;

	@BeforeEach
	void configurarHttp() {
		http = RestClient.builder().baseUrl("http://localhost:" + porta)
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				}).build();
	}

	static String emailUnico(String nome) {
		return nome + "-" + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
	}

	Tokens cadastrar(String nome, String email) {
		return http.post().uri("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("nome", nome, "email", email, "cpf", cpfValido(), "telefone", "(21) 99999-0000", "senha",
						"senha-forte-123"))
				.retrieve().body(Tokens.class);
	}

	/** Cadastra e confirma o e-mail com o código que chegou na caixa. Devolve o token de acesso. */
	String cadastrarConfirmado(String nome, String email) {
		String token = cadastrar(nome, email).accessToken();
		assertThat(confirmarEmail(token, emails.ultimoCodigo(email)).getStatusCode().value()).isEqualTo(204);
		return token;
	}

	ResponseEntity<Void> confirmarEmail(String token, String codigo) {
		return post("/api/v1/auth/confirmar-email", token, Map.of("codigo", codigo));
	}

	ResponseEntity<Tokens> login(String email, String senha) {
		return http.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", email, "senha", senha)).retrieve().toEntity(Tokens.class);
	}

	Perfil perfil(String token) {
		return http.get().uri("/api/v1/eu").header("Authorization", "Bearer " + token).retrieve().body(Perfil.class);
	}

	void depositar(String token, String valor) {
		post("/api/v1/depositos", token, Map.of("valor", new BigDecimal(valor)));
	}

	ResponseEntity<Void> transferir(String token, UUID destino, String valor, String chave) {
		var pedido = http.post().uri("/api/v1/transferencias").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("destinoId", destino, "valor", new BigDecimal(valor)));
		if (chave != null) {
			pedido = pedido.header("Idempotency-Key", chave);
		}
		return pedido.retrieve().toBodilessEntity();
	}

	ResponseEntity<Void> post(String rota, String token, Object corpo) {
		var pedido = http.post().uri(rota).contentType(MediaType.APPLICATION_JSON).body(corpo);
		if (token != null) {
			pedido = pedido.header("Authorization", "Bearer " + token);
		}
		return pedido.retrieve().toBodilessEntity();
	}

	/** CPF válido e aleatório, para cada cadastro ter o seu. */
	static String cpfValido() {
		String nove = String.valueOf(100_000_000L + (long) (Math.random() * 899_999_999L));
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
