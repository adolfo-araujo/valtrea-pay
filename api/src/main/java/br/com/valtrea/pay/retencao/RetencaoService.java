package br.com.valtrea.pay.retencao;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotina de retenção (LGPD, princípio da necessidade e arts. 15 e 16): apaga o que não precisa mais ser guardado.
 * <ul>
 * <li>Cadastros que nunca confirmaram o e-mail e não têm nenhuma transação, 30 dias depois de criados.</li>
 * <li>Transações em que nenhuma das pessoas envolvidas tem mais conta (depósitos de quem excluiu a conta, e pagamentos
 * entre duas contas excluídas). Enquanto uma das pessoas tiver conta, a transação fica no extrato dela, sem o nome de
 * quem excluiu.</li>
 * <li>Contas excluídas que não aparecem em mais nenhuma transação.</li>
 * <li>Códigos de e-mail e sessões vencidos há mais de um dia, e envios ao Finance concluídos há mais de 30 dias.</li>
 * </ul>
 */
@Service
public class RetencaoService {

	public static final Duration CADASTRO_SEM_CONFIRMACAO = Duration.ofDays(30);
	private static final Duration FOLGA = Duration.ofDays(1);
	private static final Duration ENVIOS_CONCLUIDOS = Duration.ofDays(30);

	private static final Logger log = LoggerFactory.getLogger(RetencaoService.class);

	public record Resultado(int cadastrosNaoConfirmados, int transacoes, int contasExcluidas, int codigos, int sessoes,
			int envios) {

		int total() {
			return cadastrosNaoConfirmados + transacoes + contasExcluidas + codigos + sessoes + envios;
		}

	}

	private final JdbcTemplate jdbc;

	public RetencaoService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public Resultado executar(Instant agora) {
		Timestamp limiteCadastro = Timestamp.from(agora.minus(CADASTRO_SEM_CONFIRMACAO));
		Timestamp limiteVencidos = Timestamp.from(agora.minus(FOLGA));
		Timestamp limiteEnvios = Timestamp.from(agora.minus(ENVIOS_CONCLUIDOS));

		int cadastros = jdbc.update("""
				DELETE FROM usuarios u
				 WHERE u.email_confirmado_em IS NULL
				   AND u.excluido_em IS NULL
				   AND u.criado_em < ?
				   AND NOT EXISTS (
				       SELECT 1 FROM contas c JOIN transacoes t ON t.conta_origem_id = c.id OR t.conta_destino_id = c.id
				        WHERE c.usuario_id = u.id)
				""", limiteCadastro);

		int transacoes = jdbc.update("""
				DELETE FROM transacoes t
				 USING contas cd JOIN usuarios ud ON ud.id = cd.usuario_id
				 WHERE t.conta_destino_id = cd.id
				   AND ud.excluido_em IS NOT NULL
				   AND (t.conta_origem_id IS NULL OR EXISTS (
				       SELECT 1 FROM contas co JOIN usuarios uo ON uo.id = co.usuario_id
				        WHERE co.id = t.conta_origem_id AND uo.excluido_em IS NOT NULL))
				""");

		int contas = jdbc.update("""
				DELETE FROM usuarios u
				 WHERE u.excluido_em IS NOT NULL
				   AND NOT EXISTS (
				       SELECT 1 FROM contas c JOIN transacoes t ON t.conta_origem_id = c.id OR t.conta_destino_id = c.id
				        WHERE c.usuario_id = u.id)
				""");

		int codigos = jdbc.update("DELETE FROM codigos_email WHERE expira_em < ?", limiteVencidos);
		int sessoes = jdbc.update("DELETE FROM sessoes WHERE expira_em < ?", limiteVencidos);
		int envios = jdbc.update("DELETE FROM envios_finance WHERE enviado_em IS NOT NULL AND enviado_em < ?",
				limiteEnvios);

		return new Resultado(cadastros, transacoes, contas, codigos, sessoes, envios);
	}

	/** Roda um minuto depois de a API subir e depois a cada 12 horas (os testes desligam o início automático). */
	@Scheduled(initialDelayString = "${valtrea.retencao.atraso-inicial-ms:60000}",
			fixedDelayString = "${valtrea.retencao.intervalo-ms:43200000}")
	public void agendada() {
		try {
			Resultado r = executar(Instant.now());
			if (r.total() > 0) {
				log.info("[retenção] cadastros não confirmados: {}; transações sem contas ativas: {}; "
						+ "contas excluídas removidas: {}; códigos, sessões e envios vencidos: {}",
						r.cadastrosNaoConfirmados(), r.transacoes(), r.contasExcluidas(),
						r.codigos() + r.sessoes() + r.envios());
			}
		} catch (RuntimeException e) {
			log.error("[retenção] falhou", e);
		}
	}

}
