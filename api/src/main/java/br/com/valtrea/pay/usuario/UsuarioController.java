package br.com.valtrea.pay.usuario;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.conta.Conta;
import br.com.valtrea.pay.conta.ContaRepository;
import br.com.valtrea.pay.erro.NegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Usuários")
public class UsuarioController {

	private static final int LIMITE_CONTATOS = 20;

	public record PerfilResponse(UUID id, String nome, String email, String telefone, BigDecimal saldo,
			boolean emailConfirmado) {
	}

	/** Dados públicos de outro usuário: nunca inclui CPF, telefone ou saldo. */
	public record ContatoResponse(UUID id, String nome, String emailMascarado) {
	}

	private final UsuarioRepository usuarios;
	private final ContaRepository contas;

	public UsuarioController(UsuarioRepository usuarios, ContaRepository contas) {
		this.usuarios = usuarios;
		this.contas = contas;
	}

	/** E-mail e CPF não mudam por aqui: o e-mail exigiria nova confirmação e o CPF identifica a pessoa. */
	public record AtualizarPerfilRequest(
			@NotBlank @Size(max = 120) String nome,
			@NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{10,20}", message = "telefone inválido") String telefone) {
	}

	@GetMapping("/eu")
	@Transactional(readOnly = true)
	@Operation(summary = "Perfil e saldo do usuário logado")
	public PerfilResponse eu(@AuthenticationPrincipal Jwt jwt) {
		return perfil(contaDo(jwt));
	}

	@PatchMapping("/eu")
	@Transactional
	@Operation(summary = "Troca o nome e o telefone do usuário logado")
	public PerfilResponse atualizar(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid AtualizarPerfilRequest pedido) {
		Conta conta = contaDo(jwt);
		conta.getUsuario().atualizarPerfil(pedido.nome().strip(), pedido.telefone().strip());
		return perfil(conta);
	}

	private Conta contaDo(Jwt jwt) {
		return contas.findByUsuarioId(UUID.fromString(jwt.getSubject()))
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
	}

	private static PerfilResponse perfil(Conta conta) {
		Usuario usuario = conta.getUsuario();
		return new PerfilResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getTelefone(),
				conta.getSaldo(), usuario.isEmailConfirmado());
	}

	@GetMapping("/contatos")
	@Operation(summary = "Procura para quem pagar, pelo e-mail completo ou por parte do nome (mínimo 3 letras)")
	public List<ContatoResponse> contatos(@AuthenticationPrincipal Jwt jwt, @RequestParam String busca) {
		String termo = busca.strip();
		if (termo.length() < 3) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "Digite pelo menos 3 caracteres para buscar.");
		}
		UUID usuarioId = UUID.fromString(jwt.getSubject());
		String nome = Texto.paraBusca(termo).replace("%", "").replace("_", "");
		return usuarios.buscarContatos(usuarioId, termo, nome, PageRequest.of(0, LIMITE_CONTATOS)).stream()
				.map(u -> new ContatoResponse(u.getId(), u.getNome(), mascararEmail(u.getEmail())))
				.toList();
	}

	@GetMapping("/contatos/{id}")
	@Operation(summary = "Mostra quem vai receber a partir do QR Code ou do link de pagamento (o id vem no código)")
	public ContatoResponse contato(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		if (id.equals(UUID.fromString(jwt.getSubject()))) {
			throw new NegocioException(HttpStatus.BAD_REQUEST, "Este é o seu próprio código de recebimento.");
		}
		Usuario u = usuarios.findById(id)
				.orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Código de pagamento inválido."));
		return new ContatoResponse(u.getId(), u.getNome(), mascararEmail(u.getEmail()));
	}

	static String mascararEmail(String email) {
		int arroba = email.indexOf('@');
		if (arroba <= 1) {
			return "***" + email.substring(Math.max(arroba, 0));
		}
		return email.substring(0, Math.min(2, arroba - 1)) + "***" + email.substring(arroba);
	}

}
