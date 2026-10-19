package br.com.valtrea.pay.finance;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.finance.IntegracaoFinanceService.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/integracoes/finance")
@Tag(name = "Valtrea Finance")
public class IntegracaoFinanceController {

	public record ConectarRequest(@NotBlank @Size(max = 100) String codigo) {
	}

	private final IntegracaoFinanceService service;

	public IntegracaoFinanceController(IntegracaoFinanceService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "Se a conta está ligada ao Valtrea Finance")
	public Status status(@AuthenticationPrincipal Jwt jwt) {
		return service.status(usuarioId(jwt));
	}

	@PutMapping
	@Operation(summary = "Liga ao Finance com o código gerado no painel dele. A partir daí, pagamentos e saldo vão "
			+ "para lá sozinhos")
	public Status conectar(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ConectarRequest pedido) {
		return service.conectar(usuarioId(jwt), pedido.codigo());
	}

	@DeleteMapping
	@Operation(summary = "Desliga do Finance. O que já foi lançado lá continua")
	public Status desconectar(@AuthenticationPrincipal Jwt jwt) {
		return service.desconectar(usuarioId(jwt));
	}

	private static UUID usuarioId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
