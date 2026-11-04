package br.com.valtrea.pay.protecao;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Conta os pedidos de cada endereço IP em janelas de tempo fixas, em memória. Simples e suficiente para uma cópia
 * só da API: protege contra robôs e contra quem tenta adivinhar senhas ou disparar e-mails em massa.
 */
@Component
public class LimiteRequisicoes {

	private record Janela(long inicioMs, int contagem) {
	}

	private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

	/**
	 * Registra um pedido e diz se ele passou do limite.
	 *
	 * @return vazio se o pedido pode seguir; senão, em quantos segundos a janela termina
	 */
	public OptionalLong registrar(String chave, int limite, Duration janela, long agoraMs) {
		long duracao = janela.toMillis();
		Janela atual = janelas.compute(chave, (k, anterior) -> anterior == null || agoraMs - anterior.inicioMs() >= duracao
				? new Janela(agoraMs, 1)
				: new Janela(anterior.inicioMs(), anterior.contagem() + 1));
		if (atual.contagem() <= limite) {
			return OptionalLong.empty();
		}
		long restanteMs = duracao - (agoraMs - atual.inicioMs());
		return OptionalLong.of(Math.max(1, (restanteMs + 999) / 1000));
	}

	/** Apaga as janelas que já terminaram, para a memória não crescer. A maior janela é de 15 minutos. */
	@Scheduled(fixedDelay = 60_000)
	public void limparVencidas() {
		long agora = System.currentTimeMillis();
		long maiorJanela = Duration.ofHours(1).toMillis();
		janelas.entrySet().removeIf(e -> agora - e.getValue().inicioMs() > maiorJanela);
	}

	int tamanho() {
		return janelas.size();
	}

}
