package br.com.valtrea.pay.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "valtrea")
public record ValtreaProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Login login, @Valid @NotNull Codigo codigo,
		@Valid @NotNull Email email, @Valid @NotNull Sandbox sandbox) {

	/**
	 * @param validade        quanto dura o token de acesso (curto)
	 * @param validadeSessao  quanto dura a sessão do app, renovada sozinha enquanto a pessoa usa
	 */
	public record Jwt(@NotNull @Size(min = 32, message = "precisa ter pelo menos 32 caracteres") String segredo,
			@NotNull Duration validade, @NotNull Duration validadeSessao) {
	}

	public record Login(@Min(1) int tentativasMaximas, @NotNull Duration bloqueio) {
	}

	/** Códigos de 6 dígitos enviados por e-mail. */
	public record Codigo(@NotNull Duration validade, @Min(1) int tentativasMaximas, @NotNull Duration intervaloReenvio) {
	}

	public record Email(@NotBlank String remetente) {
	}

	public record Sandbox(boolean habilitado) {
	}

}
