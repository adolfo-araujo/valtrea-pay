package br.com.valtrea.pay.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.finance.EnvioFinance.Tipo;
import br.com.valtrea.pay.transacao.TipoTransacao;
import br.com.valtrea.pay.transacao.Transacao;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IntegracaoFinanceService {

	private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");

	public record Status(boolean disponivel, boolean conectado, Instant conectadoEm) {
	}

	private final IntegracaoFinanceRepository integracoes;
	private final EnvioFinanceRepository envios;
	private final ContaRepository contas;
	private final FinanceClient finance;
	private final Cifra cifra;
	private final JsonMapper json;
	private final boolean habilitada;

	public IntegracaoFinanceService(IntegracaoFinanceRepository integracoes, EnvioFinanceRepository envios,
			ContaRepository contas, FinanceClient finance, Cifra cifra, JsonMapper json,
			ValtreaProperties propriedades) {
		this.integracoes = integracoes;
		this.envios = envios;
		this.contas = contas;
		this.finance = finance;
		this.cifra = cifra;
		this.json = json;
		this.habilitada = propriedades.finance().habilitada();
	}

	@Transactional(readOnly = true)
	public Status status(UUID usuarioId) {
		Optional<IntegracaoFinance> integracao = integracoes.findById(usuarioId);
		return new Status(habilitada, integracao.isPresent(), integracao.map(IntegracaoFinance::getConectadoEm)
				.orElse(null));
	}

	/**
	 * Liga a conta ao Finance. Antes de guardar, testa o código mandando o saldo atual: assim um código errado é
	 * recusado na hora, e o painel do Finance já mostra a carteira.
	 */
	@Transactional
	public Status conectar(UUID usuarioId, String token) {
		if (!habilitada) {
			throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE, "A integração com o Finance está desligada.");
		}
		String codigo = token.strip();
		if (!codigo.startsWith("vfp_")) {
			throw new NegocioException(HttpStatus.BAD_REQUEST,
					"Código inválido. Gere um no painel do Valtrea Finance, no cartão Carteira Valtrea Pay.");
		}
		Conta conta = contas.findByUsuarioId(usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));

		switch (finance.atualizarSaldo(codigo, saldo(conta))) {
			case FinanceClient.Resultado.Ok ok -> {
			}
			case FinanceClient.Resultado.NaoAutorizado n -> throw new NegocioException(HttpStatus.BAD_REQUEST,
					"O Finance não reconheceu este código. Gere um novo e tente de novo.");
			case FinanceClient.Resultado.Temporario t -> throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE,
					"Não foi possível falar com o Valtrea Finance agora. Tente de novo em instantes.");
			case FinanceClient.Resultado.Recusado r -> throw new NegocioException(HttpStatus.BAD_GATEWAY,
					"O Valtrea Finance recusou a conexão.");
		}

		String cifrado = cifra.cifrar(codigo);
		integracoes.findById(usuarioId).ifPresentOrElse(i -> i.trocarToken(cifrado),
				() -> integracoes.save(new IntegracaoFinance(usuarioId, cifrado)));
		return status(usuarioId);
	}

	@Transactional
	public Status desconectar(UUID usuarioId) {
		integracoes.deleteById(usuarioId);
		envios.apagarPendentes(usuarioId);
		return new Status(habilitada, false, null);
	}

	/**
	 * Põe na fila os envios de uma movimentação, para quem tiver o Finance conectado. Chamado dentro da transação do
	 * pagamento: se o pagamento falhar, nada vai para a fila.
	 */
	public void registrar(Transacao transacao) {
		if (!habilitada) {
			return;
		}
		if (transacao.getOrigem() != null) {
			registrarLado(transacao, transacao.getOrigem(), transacao.getDestino(), "SAIDA");
		}
		registrarLado(transacao, transacao.getDestino(), transacao.getOrigem(), "ENTRADA");
	}

	private void registrarLado(Transacao t, Conta conta, Conta outra, String lado) {
		UUID usuarioId = conta.getUsuario().getId();
		if (!integracoes.existsById(usuarioId)) {
			return;
		}
		// Depósito é dinheiro da própria pessoa entrando na carteira, não um ganho: só atualiza o saldo.
		if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
			boolean saida = lado.equals("SAIDA");
			String nome = "Valtrea Pay · " + (saida ? "para " : "de ") + outra.getUsuario().getNome();
			Map<String, Object> lancamento = new LinkedHashMap<>();
			lancamento.put("external_id", "pay:" + t.getId() + ":" + lado);
			lancamento.put("name", nome);
			lancamento.put("amount", t.getValor());
			lancamento.put("type", saida ? "EXPENSE" : "EARNING");
			lancamento.put("date", LocalDate.ofInstant(t.getCriadoEm(), BRASIL).toString());
			envios.save(new EnvioFinance(usuarioId, Tipo.TRANSACAO, json.writeValueAsString(lancamento)));
		}
		envios.save(new EnvioFinance(usuarioId, Tipo.SALDO, saldo(conta)));
	}

	private String saldo(Conta conta) {
		Map<String, Object> saldo = new LinkedHashMap<>();
		saldo.put("balance", conta.getSaldo());
		saldo.put("at", Instant.now().toString());
		return json.writeValueAsString(saldo);
	}

	/** Usado pelo processo de envio. */
	Optional<String> tokenDe(UUID usuarioId) {
		return integracoes.findById(usuarioId).map(i -> cifra.decifrar(i.getTokenCifrado()));
	}

}
