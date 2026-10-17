package br.com.valtrea.pay.auth;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.valtrea.pay.auth.CodigoEmail.Finalidade;
import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.email.Email;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.usuario.Usuario;

/** Emite e confere os códigos de 6 dígitos. Deve ser chamado dentro de uma transação de quem usa. */
@Service
public class CodigoService {

	private static final String CODIGO_INVALIDO = "Código inválido ou expirado. Peça um novo.";

	private final CodigoEmailRepository codigos;
	private final Segredos segredos;
	private final ApplicationEventPublisher eventos;
	private final ValtreaProperties.Codigo regras;

	public CodigoService(CodigoEmailRepository codigos, Segredos segredos, ApplicationEventPublisher eventos,
			ValtreaProperties propriedades) {
		this.codigos = codigos;
		this.segredos = segredos;
		this.eventos = eventos;
		this.regras = propriedades.codigo();
	}

	/**
	 * Gera um código novo e manda por e-mail.
	 *
	 * @return {@code false} se o último código foi enviado há pouco tempo e nada foi enviado agora
	 */
	public boolean emitir(Usuario usuario, Finalidade finalidade) {
		Instant agora = Instant.now();
		boolean muitoCedo = codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), finalidade)
				.map(c -> c.getCriadoEm().plus(regras.intervaloReenvio()).isAfter(agora))
				.orElse(false);
		if (muitoCedo) {
			return false;
		}

		String codigo = segredos.novoCodigo();
		codigos.save(new CodigoEmail(usuario, finalidade, segredos.hashCodigo(codigo), agora.plus(regras.validade())));
		long minutos = regras.validade().toMinutes();
		eventos.publishEvent(switch (finalidade) {
			case CONFIRMAR_EMAIL -> Email.confirmarEmail(usuario.getEmail(), usuario.getNome(), codigo, minutos);
			case REDEFINIR_SENHA -> Email.redefinirSenha(usuario.getEmail(), usuario.getNome(), codigo, minutos);
		});
		return true;
	}

	/**
	 * Confere o código e o marca como usado. Cada erro gasta uma tentativa, então a transação de quem chama precisa
	 * gravar mesmo quando este método lança {@link NegocioException}.
	 */
	public void verificar(Usuario usuario, Finalidade finalidade, String codigo) {
		CodigoEmail atual = codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), finalidade)
				.filter(c -> c.isValido(Instant.now(), regras.tentativasMaximas()))
				.orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST, CODIGO_INVALIDO));

		atual.registrarTentativa();
		if (!Segredos.iguais(atual.getCodigoHash(), segredos.hashCodigo(codigo))) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "Código incorreto.");
		}
		atual.marcarUsado();
	}

}
