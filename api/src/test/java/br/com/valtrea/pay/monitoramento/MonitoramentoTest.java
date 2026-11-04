package br.com.valtrea.pay.monitoramento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sentry.SentryOptions;

class MonitoramentoTest {

	@Test
	void semDsnNadaESenviado() {
		assertThat(new Monitoramento("", "test").isAtivo()).isFalse();
		assertThat(new Monitoramento("  ", "test").isAtivo()).isFalse();
	}

	@Test
	void naoEnviaDadosPessoais() {
		SentryOptions opcoes = new SentryOptions();
		Monitoramento.configurar(opcoes, "https://chave@o0.ingest.sentry.io/0", "test");
		assertThat(opcoes.isSendDefaultPii()).isFalse();
		assertThat(opcoes.getTracesSampleRate()).isNull();
		assertThat(opcoes.getBeforeSend()).isNotNull();
	}

	@Test
	void avisoDeLentidaoNoMaximoUmPorRotaACada15Minutos() {
		Monitoramento m = new Monitoramento("", "test");
		long agora = 1_000_000_000L;
		assertThat(m.podeAvisar("GET /api/v1/eu", agora)).isTrue();
		assertThat(m.podeAvisar("GET /api/v1/eu", agora + 60_000)).isFalse();
		assertThat(m.podeAvisar("GET /api/v1/transacoes", agora + 60_000)).isTrue();
		assertThat(m.podeAvisar("GET /api/v1/eu", agora + Monitoramento.INTERVALO_AVISO_LENTO.toMillis())).isTrue();
	}

}
