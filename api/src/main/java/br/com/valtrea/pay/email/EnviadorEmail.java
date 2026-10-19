package br.com.valtrea.pay.email;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.valtrea.pay.config.ValtreaProperties;

/**
 * Envia os e-mails publicados pelos serviços, depois que a transação do banco foi gravada. Em produção usa a API HTTP do
 * Resend (o plano gratuito do Render bloqueia SMTP); no desenvolvimento, SMTP para o Mailpit.
 */
@Component
public class EnviadorEmail {

	private static final Logger log = LoggerFactory.getLogger(EnviadorEmail.class);

	private final JavaMailSender mailSender;
	private final String remetente;
	private final RestClient resend;

	public EnviadorEmail(JavaMailSender mailSender, ValtreaProperties propriedades) {
		this.mailSender = mailSender;
		this.remetente = propriedades.email().remetente();
		this.resend = propriedades.email().usaResend() ? criarClienteResend(propriedades.email().resendApiKey()) : null;
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void enviar(Email email) {
		try {
			if (resend != null) {
				enviarPeloResend(email);
			} else {
				enviarPorSmtp(email);
			}
		} catch (MailException | RestClientException | IllegalStateException e) {
			// A operação já foi gravada; a pessoa pode pedir o código de novo. Não registramos o conteúdo (tem códigos).
			log.error("Falha ao enviar e-mail \"{}\": {}", email.assunto().replaceAll("\\d{6}", "******"),
					e.getMessage());
		}
	}

	private void enviarPorSmtp(Email email) {
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(remetente);
		mensagem.setTo(email.para());
		mensagem.setSubject(email.assunto());
		mensagem.setText(email.texto());
		mailSender.send(mensagem);
	}

	private void enviarPeloResend(Email email) {
		var resposta = resend.post()
				.uri("/emails")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("from", remetente, "to", List.of(email.para()), "subject", email.assunto(), "text",
						email.texto()))
				.retrieve()
				.toEntity(String.class);
		if (resposta.getStatusCode().isError()) {
			throw new IllegalStateException("Resend respondeu " + resposta.getStatusCode().value() + ": "
					+ resposta.getBody());
		}
	}

	private static RestClient criarClienteResend(String chave) {
		var fabrica = new JdkClientHttpRequestFactory();
		fabrica.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder()
				.baseUrl("https://api.resend.com")
				.defaultHeader("Authorization", "Bearer " + chave)
				.requestFactory(fabrica)
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				})
				.build();
	}

}
