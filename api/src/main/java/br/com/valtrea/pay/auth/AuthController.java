package br.com.valtrea.pay.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.auth.AuthDtos.CadastroRequest;
import br.com.valtrea.pay.auth.AuthDtos.LoginRequest;
import br.com.valtrea.pay.auth.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
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
	@Operation(summary = "Cria um usuário com uma carteira de saldo zero e já devolve o token de acesso")
	public TokenResponse cadastrar(@RequestBody @Valid CadastroRequest pedido) {
		return authService.cadastrar(pedido);
	}

	@PostMapping("/login")
	@Operation(summary = "Entra com e-mail e senha. Bloqueia temporariamente após várias senhas erradas")
	public TokenResponse entrar(@RequestBody @Valid LoginRequest pedido) {
		return authService.entrar(pedido);
	}

}
