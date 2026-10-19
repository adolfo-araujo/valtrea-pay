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
		@Valid @NotNull Email email, @Valid @NotNull Sandbox sandbox, @Valid @NotNull Finance finance) {

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

	/** @param resendApiKey preenchida, os e-mails saem pela API HTTP do Resend; vazia, por SMTP */
	public record Email(@NotBlank String remetente, String resendApiKey) {

		public boolean usaResend() {
			return resendApiKey != null && !resendApiKey.isBlank();
		}

	}

	public record Sandbox(boolean habilitado) {
	}

	/**
	 * Integração com o Valtrea Finance.
	 *
	 * @param url              endereço da API do Finance (ex.: https://api.finance.valtrea.com.br/api); vazio desliga
	 * @param chaveCifra       32 bytes em Base64 para cifrar os códigos de conexão guardados
	 * @param intervaloEnvioMs de quanto em quanto tempo a fila de envios é processada
	 */
	public record Finance(String url, @NotBlank String chaveCifra, @Min(100) long intervaloEnvioMs) {

		public boolean habilitada() {
			return url != null && !url.isBlank();
		}

	}

}
