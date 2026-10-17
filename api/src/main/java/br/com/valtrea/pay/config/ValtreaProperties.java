package br.com.valtrea.pay.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "valtrea")
public record ValtreaProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Login login, @Valid @NotNull Sandbox sandbox) {

	public record Jwt(@NotNull @Size(min = 32, message = "precisa ter pelo menos 32 caracteres") String segredo,
			@NotNull Duration validade) {
	}

	public record Login(@Min(1) int tentativasMaximas, @NotNull Duration bloqueio) {
	}

	public record Sandbox(boolean habilitado) {
	}

}
