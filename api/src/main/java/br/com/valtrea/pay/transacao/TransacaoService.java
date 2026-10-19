package br.com.valtrea.pay.transacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.finance.IntegracaoFinanceService;
import br.com.valtrea.pay.transacao.TransacaoDtos.Contraparte;
import br.com.valtrea.pay.transacao.TransacaoDtos.Direcao;
import br.com.valtrea.pay.transacao.TransacaoDtos.Pagina;
import br.com.valtrea.pay.transacao.TransacaoDtos.TransacaoResponse;
import br.com.valtrea.pay.transacao.TransacaoDtos.TransferenciaRequest;
import br.com.valtrea.pay.usuario.Usuario;

@Service
public class TransacaoService {

	private static final int TAMANHO_MAXIMO_PAGINA = 50;

	private final ContaRepository contas;
	private final TransacaoRepository transacoes;
	private final IntegracaoFinanceService finance;

	public TransacaoService(ContaRepository contas, TransacaoRepository transacoes, IntegracaoFinanceService finance) {
		this.contas = contas;
		this.transacoes = transacoes;
		this.finance = finance;
	}

	/**
	 * Move dinheiro entre duas carteiras numa única transação de banco, com as duas contas travadas. Quem paga é
	 * sempre o usuário do token, nunca um campo enviado pelo app. Repetir o pedido com a mesma chave de idempotência
	 * devolve a transferência já feita, sem cobrar de novo.
	 */
	@Transactional
	public TransacaoResponse transferir(UUID usuarioId, TransferenciaRequest pedido, String chaveIdempotencia) {
		if (usuarioId.equals(pedido.destinoId())) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "Não é possível transferir para você mesmo.");
		}

		List<Conta> travadas = contas.travarPorUsuarioIds(Set.of(usuarioId, pedido.destinoId()));
		Conta origem = contaDoUsuario(travadas, usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
		Conta destino = contaDoUsuario(travadas, pedido.destinoId())
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Destinatário não encontrado."));

		if (!origem.getUsuario().isEmailConfirmado()) {
			throw new NegocioException(HttpStatus.FORBIDDEN, "Confirme seu e-mail para enviar pagamentos.");
		}

		if (chaveIdempotencia != null) {
			Optional<Transacao> repetida = transacoes.findByOrigemIdAndChaveIdempotencia(origem.getId(),
					chaveIdempotencia);
			if (repetida.isPresent()) {
				return repetidaOuConflito(repetida.get(), destino, pedido.valor(), origem);
			}
		}

		BigDecimal valor = pedido.valor().setScale(2);
		origem.debitar(valor);
		destino.creditar(valor);
		String descricao = pedido.descricao() == null || pedido.descricao().isBlank() ? null
				: pedido.descricao().strip();
		Transacao transacao = transacoes
				.save(Transacao.transferencia(origem, destino, valor, descricao, chaveIdempotencia));
		finance.registrar(transacao);
		return paraResposta(transacao, origem);
	}

	@Transactional
	public TransacaoResponse depositar(UUID usuarioId, BigDecimal valor) {
		Conta conta = contaDoUsuario(contas.travarPorUsuarioIds(Set.of(usuarioId)), usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
		BigDecimal valorAjustado = valor.setScale(2);
		conta.creditar(valorAjustado);
		Transacao deposito = transacoes.save(Transacao.deposito(conta, valorAjustado));
		finance.registrar(deposito);
		return paraResposta(deposito, conta);
	}

	@Transactional(readOnly = true)
	public Pagina<TransacaoResponse> extrato(UUID usuarioId, int pagina, int tamanho) {
		Conta conta = contas.findByUsuarioId(usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
		PageRequest pedido = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamanho, 1, TAMANHO_MAXIMO_PAGINA),
				Sort.by(Sort.Direction.DESC, "criadoEm"));
		return Pagina.de(transacoes.extrato(conta.getId(), pedido).map(t -> paraResposta(t, conta)));
	}

	private TransacaoResponse repetidaOuConflito(Transacao anterior, Conta destino, BigDecimal valor, Conta origem) {
		boolean mesmoPedido = anterior.getDestino().getId().equals(destino.getId())
				&& anterior.getValor().compareTo(valor) == 0;
		if (!mesmoPedido) {
			throw new NegocioException(HttpStatus.CONFLICT,
					"Esta chave de idempotência já foi usada em outra transferência.");
		}
		return paraResposta(anterior, origem);
	}

	private static Optional<Conta> contaDoUsuario(List<Conta> contas, UUID usuarioId) {
		return contas.stream().filter(c -> c.getUsuario().getId().equals(usuarioId)).findFirst();
	}

	private static TransacaoResponse paraResposta(Transacao t, Conta doPontoDeVista) {
		boolean saida = t.getOrigem() != null && Objects.equals(t.getOrigem().getId(), doPontoDeVista.getId());
		Conta outra = saida ? t.getDestino() : t.getOrigem();
		Contraparte contraparte = null;
		if (outra != null) {
			Usuario u = outra.getUsuario();
			contraparte = new Contraparte(u.getId(), u.getNome());
		}
		return new TransacaoResponse(t.getId(), t.getTipo(), saida ? Direcao.SAIDA : Direcao.ENTRADA, t.getValor(),
				t.getDescricao(), contraparte, t.getCriadoEm());
	}

}
