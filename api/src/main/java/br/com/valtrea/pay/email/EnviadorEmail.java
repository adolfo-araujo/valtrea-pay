package br.com.valtrea.pay.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.valtrea.pay.config.ValtreaProperties;

/** Envia por SMTP os e-mails publicados pelos serviços, depois que a transação do banco foi gravada. */
@Component
public class EnviadorEmail {

	private static final Logger log = LoggerFactory.getLogger(EnviadorEmail.class);

	private final JavaMailSender mailSender;
	private final String remetente;

	public EnviadorEmail(JavaMailSender mailSender, ValtreaProperties propriedades) {
		this.mailSender = mailSender;
		this.remetente = propriedades.email().remetente();
	}

	@TransactionalEventListener(fallbackExecution = true)
	public void enviar(Email email) {
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(remetente);
		mensagem.setTo(email.para());
		mensagem.setSubject(email.assunto());
		mensagem.setText(email.texto());
		try {
			mailSender.send(mensagem);
		} catch (MailException e) {
			// A operação já foi gravada; a pessoa pode pedir o código de novo. Não registramos o conteúdo (tem códigos).
			log.error("Falha ao enviar e-mail \"{}\": {}", email.assunto().replaceAll("\\d{6}", "******"),
					e.getMessage());
		}
	}

}
