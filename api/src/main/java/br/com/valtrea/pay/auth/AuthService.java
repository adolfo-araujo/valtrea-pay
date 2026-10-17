package br.com.valtrea.pay.auth;

import java.time.Instant;
import java.util.Locale;

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
import br.com.valtrea.pay.auth.AuthDtos.TokenResponse;
import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.usuario.Cpf;
import br.com.valtrea.pay.usuario.Usuario;
import br.com.valtrea.pay.usuario.UsuarioRepository;

@Service
public class AuthService {

	private static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

	private final UsuarioRepository usuarios;
	private final ContaRepository contas;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final ValtreaProperties propriedades;

	/** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails têm conta. */
	private final String hashFicticio;

	public AuthService(UsuarioRepository usuarios, ContaRepository contas, PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder, ValtreaProperties propriedades) {
		this.usuarios = usuarios;
		this.contas = contas;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
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
		return gerarToken(usuario);
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
					"Muitas tentativas de login. Tente novamente mais tarde.");
		}
		if (!passwordEncoder.matches(pedido.senha(), usuario.getSenhaHash())) {
			ValtreaProperties.Login regras = propriedades.login();
			usuario.registrarFalhaLogin(regras.tentativasMaximas(), agora.plus(regras.bloqueio()));
			throw new NegocioException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS);
		}

		usuario.registrarLoginComSucesso();
		return gerarToken(usuario);
	}

	private TokenResponse gerarToken(Usuario usuario) {
		Instant agora = Instant.now();
		Instant expiraEm = agora.plus(propriedades.jwt().validade());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("valtrea-pay")
				.subject(usuario.getId().toString())
				.issuedAt(agora)
				.expiresAt(expiraEm)
				.build();
		String token = jwtEncoder
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new TokenResponse(token, "Bearer", expiraEm);
	}

	private static String normalizarEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
