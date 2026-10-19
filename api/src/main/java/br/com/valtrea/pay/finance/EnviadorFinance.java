package br.com.valtrea.pay.finance;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.valtrea.pay.config.ValtreaProperties;

/** Processa a fila de envios para o Finance em segundo plano, tentando de novo quando o Finance falha. */
@Component
public class EnviadorFinance {

	private static final Logger log = LoggerFactory.getLogger(EnviadorFinance.class);
	private static final int LOTE = 50;

	private final EnvioFinanceRepository envios;
	private final IntegracaoFinanceService integracao;
	private final IntegracaoFinanceRepository integracoes;
	private final FinanceClient finance;
	private final boolean habilitada;

	public EnviadorFinance(EnvioFinanceRepository envios, IntegracaoFinanceService integracao,
			IntegracaoFinanceRepository integracoes, FinanceClient finance, ValtreaProperties propriedades) {
		this.envios = envios;
		this.integracao = integracao;
		this.integracoes = integracoes;
		this.finance = finance;
		this.habilitada = propriedades.finance().habilitada();
	}

	@Scheduled(fixedDelayString = "${valtrea.finance.intervalo-envio-ms}")
	@Transactional
	public void processarFila() {
		if (!habilitada) {
			return;
		}
		for (EnvioFinance envio : envios.travarPendentes(LOTE)) {
			Optional<String> token = integracao.tokenDe(envio.getUsuarioId());
			if (token.isEmpty()) {
				envio.descartar("conta desconectada");
				continue;
			}
			var resultado = switch (envio.getTipo()) {
				case TRANSACAO -> finance.lancarTransacao(token.get(), envio.getConteudo());
				case SALDO -> finance.atualizarSaldo(token.get(), envio.getConteudo());
			};
			switch (resultado) {
				case FinanceClient.Resultado.Ok ok -> envio.marcarEnviado();
				case FinanceClient.Resultado.NaoAutorizado n -> {
					// A pessoa revogou o código no Finance: desliga a conexão aqui também.
					log.info("Código do Finance revogado; desconectando o usuário {}", envio.getUsuarioId());
					envio.descartar("código revogado no Finance");
					integracoes.deleteById(envio.getUsuarioId());
				}
				case FinanceClient.Resultado.Temporario t -> envio.falhou(t.motivo());
				case FinanceClient.Resultado.Recusado r -> {
					log.warn("Finance recusou um envio {}: {}", envio.getTipo(), r.motivo());
					envio.descartar(r.motivo());
				}
			}
		}
	}

}
