package br.com.valtrea.pay.auth;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.auth.AuthDtos.CadastroRequest;
import br.com.valtrea.pay.auth.AuthDtos.CodigoRequest;
import br.com.valtrea.pay.auth.AuthDtos.EsqueciSenhaRequest;
import br.com.valtrea.pay.auth.AuthDtos.LoginRequest;
import br.com.valtrea.pay.auth.AuthDtos.RedefinirSenhaRequest;
import br.com.valtrea.pay.auth.AuthDtos.RenovarRequest;
import br.com.valtrea.pay.auth.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
@SecurityRequirements
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/cadastro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Cria um usuário com carteira de saldo zero, envia o código de confirmação por e-mail e "
			+ "já devolve os tokens")
	public TokenResponse cadastrar(@RequestBody @Valid CadastroRequest pedido) {
		return authService.cadastrar(pedido);
	}

	@PostMapping("/login")
	@Operation(summary = "Entra com e-mail e senha. Bloqueia por 15 minutos após 5 senhas erradas e avisa por e-mail")
	public TokenResponse entrar(@RequestBody @Valid LoginRequest pedido) {
		return authService.entrar(pedido);
	}

	@PostMapping("/renovar")
	@Operation(summary = "Troca o refreshToken por um par novo. Cada refreshToken só pode ser usado uma vez")
	public TokenResponse renovar(@RequestBody @Valid RenovarRequest pedido) {
		return authService.renovar(pedido.refreshToken());
	}

	@PostMapping("/sair")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Encerra a sessão deste aparelho")
	public void sair(@RequestBody @Valid RenovarRequest pedido) {
		authService.sair(pedido.refreshToken());
	}

	@PostMapping("/confirmar-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearer")
	@Operation(summary = "Confirma o e-mail com o código de 6 dígitos. Libera o envio de pagamentos")
	public void confirmarEmail(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid CodigoRequest pedido) {
		authService.confirmarEmail(UUID.fromString(jwt.getSubject()), pedido.codigo());
	}

	@PostMapping("/reenviar-confirmacao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@SecurityRequirement(name = "bearer")
	@Operation(summary = "Envia outro código de confirmação (no máximo um por minuto)")
	public void reenviarConfirmacao(@AuthenticationPrincipal Jwt jwt) {
		authService.reenviarConfirmacao(UUID.fromString(jwt.getSubject()));
	}

	@PostMapping("/esqueci-senha")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Envia um código para redefinir a senha, se o e-mail tiver conta. A resposta é sempre a mesma")
	public void esqueciSenha(@RequestBody @Valid EsqueciSenhaRequest pedido) {
		authService.esqueciSenha(pedido.email());
	}

	@PostMapping("/redefinir-senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Define uma senha nova com o código recebido. Desconecta todos os aparelhos")
	public void redefinirSenha(@RequestBody @Valid RedefinirSenhaRequest pedido) {
		authService.redefinirSenha(pedido);
	}

}
