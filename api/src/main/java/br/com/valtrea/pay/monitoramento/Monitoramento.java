package br.com.valtrea.pay.monitoramento;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import jakarta.annotation.PreDestroy;

/**
 * Envia ao Sentry os erros inesperados da API e os avisos de resposta lenta, para chegar um e-mail antes de alguém
 * reclamar. Sem SENTRY_DSN (desenvolvimento, testes e teste de carga) nada sai do servidor.
 * <p>
 * Privacidade: nenhum dado pessoal é enviado. O SDK não guarda IP nem usuário ({@code sendDefaultPii = false}), a API
 * não anexa corpo, cabeçalhos nem parâmetros dos pedidos, e as rotas vão como modelo ({@code /api/v1/contatos/{id}}),
 * sem o identificador de ninguém.
 */
@Component
public class Monitoramento {

	private static final Logger log = LoggerFactory.getLogger(Monitoramento.class);

	/** No máximo um aviso de lentidão por rota a cada 15 minutos (a cota gratuita do Sentry é limitada). */
	static final Duration INTERVALO_AVISO_LENTO = Duration.ofMinutes(15);

	private final boolean ativo;
	private final Map<String, Long> ultimoAvisoLento = new ConcurrentHashMap<>();

	public Monitoramento(@Value("${valtrea.monitoramento.sentry-dsn:}") String dsn,
			@Value("${valtrea.monitoramento.ambiente:production}") String ambiente) {
		this.ativo = dsn != null && !dsn.isBlank();
		if (ativo) {
			Sentry.init(opcoes -> configurar(opcoes, dsn.strip(), ambiente));
			log.info("Monitoramento de erros ativo.");
		}
	}

	static void configurar(SentryOptions opcoes, String dsn, String ambiente) {
		opcoes.setDsn(dsn);
		opcoes.setEnvironment(ambiente);
		opcoes.setRelease("valtrea-pay-api@" + versao());
		opcoes.setSendDefaultPii(false);
		// Só erros e avisos, sem medição de desempenho (economiza a cota gratuita).
		opcoes.setTracesSampleRate(null);
		opcoes.setAttachServerName(false);
		opcoes.setBeforeSend((evento, dica) -> {
			evento.setUser(null);
			evento.setRequest(null);
			return evento;
		});
	}

	private static String versao() {
		String v = Monitoramento.class.getPackage().getImplementationVersion();
		return v != null ? v : "dev";
	}

	public boolean isAtivo() {
		return ativo;
	}

	/** Erro que a API não esperava (vira 500 para o app). */
	public void erro(Throwable erro, String metodo, String rota) {
		if (!ativo) {
			return;
		}
		Sentry.withScope(escopo -> {
			escopo.setTag("rota", metodo + " " + rota);
			Sentry.captureException(erro);
		});
	}

	/**
	 * Resposta acima do limite configurado (valtrea.limites.resposta-lenta). Agrupado por rota no Sentry: dá para
	 * criar um alerta que manda e-mail quando aparecer.
	 */
	public void respostaLenta(String metodo, String rota, long ms, long agoraMs) {
		if (!ativo || !podeAvisar(metodo + " " + rota, agoraMs)) {
			return;
		}
		Sentry.withScope(escopo -> {
			escopo.setTag("tipo", "resposta-lenta");
			escopo.setTag("rota", metodo + " " + rota);
			escopo.setExtra("duracao_ms", String.valueOf(ms));
			escopo.setFingerprint(java.util.List.of("resposta-lenta", metodo, rota));
			Sentry.captureMessage("Resposta lenta: " + metodo + " " + rota + " levou " + ms + " ms",
					SentryLevel.WARNING);
		});
	}

	boolean podeAvisar(String chave, long agoraMs) {
		long intervalo = INTERVALO_AVISO_LENTO.toMillis();
		boolean[] liberado = { false };
		ultimoAvisoLento.compute(chave, (k, ultimo) -> {
			if (ultimo == null || agoraMs - ultimo >= intervalo) {
				liberado[0] = true;
				return agoraMs;
			}
			return ultimo;
		});
		return liberado[0];
	}

	@PreDestroy
	void encerrar() {
		if (ativo) {
			Sentry.close();
		}
	}

}
