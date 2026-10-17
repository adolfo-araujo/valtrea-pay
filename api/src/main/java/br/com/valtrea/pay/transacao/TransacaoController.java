package br.com.valtrea.pay.transacao;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.config.ValtreaProperties;
import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.transacao.TransacaoDtos.DepositoRequest;
import br.com.valtrea.pay.transacao.TransacaoDtos.Pagina;
import br.com.valtrea.pay.transacao.TransacaoDtos.TransacaoResponse;
import br.com.valtrea.pay.transacao.TransacaoDtos.TransferenciaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transações")
public class TransacaoController {

	private final TransacaoService transacaoService;
	private final ValtreaProperties propriedades;

	public TransacaoController(TransacaoService transacaoService, ValtreaProperties propriedades) {
		this.transacaoService = transacaoService;
		this.propriedades = propriedades;
	}

	@PostMapping("/transferencias")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Envia dinheiro da sua carteira para outro usuário")
	public TransacaoResponse transferir(@AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "Identificador único gerado pelo app para este pagamento (ex.: UUID). "
					+ "Reenviar o mesmo valor não cobra duas vezes.")
			@RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
			@RequestBody @Valid TransferenciaRequest pedido) {
		if (chaveIdempotencia != null && (chaveIdempotencia.isBlank() || chaveIdempotencia.length() > 64)) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "Idempotency-Key deve ter entre 1 e 64 caracteres.");
		}
		return transacaoService.transferir(usuarioId(jwt), pedido, chaveIdempotencia);
	}

	@GetMapping("/transacoes")
	@Operation(summary = "Extrato da carteira, do mais recente para o mais antigo")
	public Pagina<TransacaoResponse> extrato(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanho) {
		return transacaoService.extrato(usuarioId(jwt), pagina, tamanho);
	}

	@PostMapping("/depositos")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Coloca saldo de teste na carteira. Só funciona com VALTREA_SANDBOX=true")
	public TransacaoResponse depositar(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid DepositoRequest pedido) {
		if (!propriedades.sandbox().habilitado()) {
			throw new NegocioException(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
		}
		return transacaoService.depositar(usuarioId(jwt), pedido.valor());
	}

	private static UUID usuarioId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
