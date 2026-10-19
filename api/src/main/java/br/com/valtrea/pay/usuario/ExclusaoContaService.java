package br.com.valtrea.pay.usuario;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.valtrea.pay.auth.SessaoRepository;
import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.email.Email;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.finance.IntegracaoFinanceService;

/**
 * Exclusão de conta pedida pela própria pessoa (exigida pelo Google Play e pela LGPD). Apaga os dados pessoais e
 * encerra os acessos; as transações continuam, sem identificar a pessoa, porque registros financeiros precisam ser
 * guardados e o extrato de quem recebeu não pode ficar com buracos.
 */
@Service
public class ExclusaoContaService {

	private final ContaRepository contas;
	private final SessaoRepository sessoes;
	private final IntegracaoFinanceService finance;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventos;
	private final boolean sandbox;

	public ExclusaoContaService(ContaRepository contas, SessaoRepository sessoes, IntegracaoFinanceService finance,
			PasswordEncoder passwordEncoder, ApplicationEventPublisher eventos, ValtreaProperties propriedades) {
		this.contas = contas;
		this.sessoes = sessoes;
		this.finance = finance;
		this.passwordEncoder = passwordEncoder;
		this.eventos = eventos;
		this.sandbox = propriedades.sandbox().habilitado();
	}

	@Transactional
	public void excluir(UUID usuarioId, String senha) {
		// Trava a conta: um pagamento em andamento termina antes, e nenhum começa durante a exclusão.
		Conta conta = contas.travarPorUsuarioIds(Set.of(usuarioId)).stream().findFirst()
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
		Usuario usuario = conta.getUsuario();

		if (usuario.isExcluido()) {
			return;
		}
		if (!passwordEncoder.matches(senha, usuario.getSenhaHash())) {
			throw new NegocioException(HttpStatus.FORBIDDEN, "Senha incorreta.");
		}
		// Com dinheiro de verdade, o saldo precisa sair da carteira antes. No sandbox o saldo é fictício.
		if (!sandbox && conta.getSaldo().signum() > 0) {
			throw new NegocioException(HttpStatus.CONFLICT,
					"Sua carteira ainda tem saldo. Transfira o valor antes de excluir a conta.");
		}

		Email aviso = Email.contaExcluida(usuario.getEmail(), usuario.getNome());
		finance.desconectar(usuarioId);
		sessoes.revogarTodas(usuarioId, Instant.now());
		usuario.excluir(passwordEncoder.encode(UUID.randomUUID().toString()));
		eventos.publishEvent(aviso);
	}

}
