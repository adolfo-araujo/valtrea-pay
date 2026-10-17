package br.com.valtrea.pay.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record CadastroRequest(
			@NotBlank @Size(max = 120) String nome,
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank String cpf,
			@NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{10,20}", message = "telefone inválido") String telefone,
			@NotBlank @Size(min = 8, max = 72, message = "a senha precisa ter entre 8 e 72 caracteres") String senha) {
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
	}

	/**
	 * @param accessToken  vai no cabeçalho Authorization; dura pouco ({@code expiraEm})
	 * @param refreshToken guarda a sessão; troque por um novo par em /auth/renovar quando o accessToken vencer
	 */
	public record TokenResponse(String accessToken, String tipo, Instant expiraEm, String refreshToken) {
	}

	public record RenovarRequest(@NotBlank String refreshToken) {
	}

	public record CodigoRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "o código tem 6 números") String codigo) {
	}

	public record EsqueciSenhaRequest(@NotBlank @Email String email) {
	}

	public record RedefinirSenhaRequest(
			@NotBlank @Email String email,
			@NotBlank @Pattern(regexp = "\\d{6}", message = "o código tem 6 números") String codigo,
			@NotBlank @Size(min = 8, max = 72, message = "a senha precisa ter entre 8 e 72 caracteres") String novaSenha) {
	}

}
