package br.com.valtrea.pay.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.valtrea.pay.auth.AuthDtos.CadastroRequest;
import br.com.valtrea.pay.auth.AuthDtos.LoginRequest;
import br.com.valtrea.pay.auth.AuthDtos.RedefinirSenhaRequest;
import br.com.valtrea.pay.auth.AuthDtos.TokenResponse;
import br.com.valtrea.pay.auth.CodigoEmail.Finalidade;
import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.email.Email;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.usuario.Cpf;
import br.com.valtrea.pay.usuario.Usuario;
import br.com.valtrea.pay.usuario.UsuarioRepository;

@Service
public class AuthService {

	private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";
	private static final String SESSAO_INVALIDA = "Sua sessão expirou. Entre novamente.";

	private final UsuarioRepository usuarios;
	private final ContaRepository contas;
	private final SessaoRepository sessoes;
	private final CodigoService codigos;
	private final Segredos segredos;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final ApplicationEventPublisher eventos;
	private final ValtreaProperties propriedades;

	/** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails têm conta. */
	private final String hashFicticio;

	public AuthService(UsuarioRepository usuarios, ContaRepository contas, SessaoRepository sessoes,
			CodigoService codigos, Segredos segredos, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			ApplicationEventPublisher eventos, ValtreaProperties propriedades) {
		this.usuarios = usuarios;
		this.contas = contas;
		this.sessoes = sessoes;
		this.codigos = codigos;
		this.segredos = segredos;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.eventos = eventos;
		this.propriedades = propriedades;
		this.hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
	}

	@Transactional
	public TokenResponse cadastrar(CadastroRequest pedido) {
		String email = normalizarEmail(pedido.email());
		String cpf = Cpf.somenteDigitos(pedido.cpf());

		if (!Cpf.isValido(cpf)) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "CPF inválido.");
		}
		if (usuarios.existsByEmail(email) || usuarios.existsByCpf(cpf)) {
			throw new NegocioException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail ou CPF.");
		}

		Usuario usuario = usuarios.save(new Usuario(pedido.nome().strip(), email, cpf, pedido.telefone().strip(),
				passwordEncoder.encode(pedido.senha())));
		contas.save(new Conta(usuario));
		codigos.emitir(usuario, Finalidade.CONFIRMAR_EMAIL);
		return iniciarSessao(usuario);
	}

	@Transactional(noRollbackFor = NegocioException.class)
	public TokenResponse entrar(LoginRequest pedido) {
		Instant agora = Instant.now();
		Usuario usuario = usuarios.findByEmail(normalizarEmail(pedido.email())).orElse(null);

		if (usuario == null) {
			passwordEncoder.matches(pedido.senha(), hashFicticio);
			throw new NegocioException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
		}
		if (usuario.isBloqueado(agora)) {
			throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
					"Muitas tentativas de login. Tente novamente mais tarde ou redefina sua senha.");
		}
		if (!passwordEncoder.matches(pedido.senha(), usuario.getSenhaHash())) {
			ValtreaProperties.Login regras = propriedades.login();
			if (usuario.registrarFalhaLogin(regras.tentativasMaximas(), agora.plus(regras.bloqueio()))) {
				eventos.publishEvent(
						Email.contaBloqueada(usuario.getEmail(), usuario.getNome(), regras.bloqueio().toMinutes()));
			}
			throw new NegocioException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
		}

		usuario.registrarLoginComSucesso();
		return iniciarSessao(usuario);
	}

	/**
	 * Troca um token de sessão por um par novo. Cada token vale uma vez: se um token já usado aparecer de novo, alguém
	 * pode tê-lo copiado, então todas as sessões da conta são encerradas.
	 */
	@Transactional(noRollbackFor = NegocioException.class)
	public TokenResponse renovar(String refreshToken) {
		Instant agora = Instant.now();
		Sessao sessao = sessoes.travarPorTokenHash(segredos.hashToken(refreshToken))
				.orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, SESSAO_INVALIDA));
		Usuario usuario = sessao.getUsuario();

		if (sessao.isRevogada()) {
			sessoes.revogarTodas(usuario.getId(), agora);
			throw new NegocioException(HttpStatus.UNAUTHORIZED, SESSAO_INVALIDA);
		}
		if (sessao.isExpirada(agora)) {
			throw new NegocioException(HttpStatus.UNAUTHORIZED, SESSAO_INVALIDA);
		}

		sessao.revogar();
		return iniciarSessao(usuario);
	}

	@Transactional
	public void sair(String refreshToken) {
		sessoes.travarPorTokenHash(segredos.hashToken(refreshToken)).ifPresent(Sessao::revogar);
	}

	@Transactional(noRollbackFor = NegocioException.class)
	public void confirmarEmail(UUID usuarioId, String codigo) {
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
		if (usuario.isEmailConfirmado()) {
			return;
		}
		codigos.verificar(usuario, Finalidade.CONFIRMAR_EMAIL, codigo);
		usuario.confirmarEmail();
	}

	@Transactional
	public void reenviarConfirmacao(UUID usuarioId) {
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
		if (usuario.isEmailConfirmado()) {
			return;
		}
		if (!codigos.emitir(usuario, Finalidade.CONFIRMAR_EMAIL)) {
			throw new NegocioException(HttpStatus.TOO_MANY_REQUESTS,
					"Aguarde um minuto antes de pedir outro código.");
		}
	}

	/** Sempre termina sem erro, exista ou não a conta, para não revelar quais e-mails estão cadastrados. */
	@Transactional
	public void esqueciSenha(String email) {
		usuarios.findByEmail(normalizarEmail(email)).ifPresent(u -> codigos.emitir(u, Finalidade.REDEFINIR_SENHA));
	}

	@Transactional(noRollbackFor = NegocioException.class)
	public void redefinirSenha(RedefinirSenhaRequest pedido) {
		Usuario usuario = usuarios.findByEmail(normalizarEmail(pedido.email()))
				.orElseThrow(() -> new NegocioException(HttpStatus.BAD_REQUEST,
						"Código inválido ou expirado. Peça um novo."));
		codigos.verificar(usuario, Finalidade.REDEFINIR_SENHA, pedido.codigo());

		usuario.trocarSenha(passwordEncoder.encode(pedido.novaSenha()));
		// Quem recebeu o código no e-mail provou que o e-mail é dela.
		usuario.confirmarEmail();
		sessoes.revogarTodas(usuario.getId(), Instant.now());
		eventos.publishEvent(Email.senhaAlterada(usuario.getEmail(), usuario.getNome()));
	}

	private TokenResponse iniciarSessao(Usuario usuario) {
		Instant agora = Instant.now();
		String refreshToken = segredos.novoTokenSessao();
		sessoes.save(new Sessao(usuario, segredos.hashToken(refreshToken),
				agora.plus(propriedades.jwt().validadeSessao())));

		Instant expiraEm = agora.plus(propriedades.jwt().validade());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("valtrea-pay")
				.subject(usuario.getId().toString())
				.issuedAt(agora)
				.expiresAt(expiraEm)
				.build();
		String accessToken = jwtEncoder
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new TokenResponse(accessToken, "Bearer", expiraEm, refreshToken);
	}

	private static String normalizarEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
