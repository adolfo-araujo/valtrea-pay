package br.com.valtrea.pay.finance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "integracoes_finance")
public class IntegracaoFinance {

	@Id
	@Column(name = "usuario_id")
	private UUID usuarioId;

	@Column(name = "token_cifrado", nullable = false)
	private String tokenCifrado;

	@Column(name = "conectado_em", nullable = false)
	private Instant conectadoEm;

	protected IntegracaoFinance() {
	}

	public IntegracaoFinance(UUID usuarioId, String tokenCifrado) {
		this.usuarioId = usuarioId;
		this.tokenCifrado = tokenCifrado;
		this.conectadoEm = Instant.now();
	}

	public void trocarToken(String tokenCifrado) {
		this.tokenCifrado = tokenCifrado;
		this.conectadoEm = Instant.now();
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public String getTokenCifrado() {
		return tokenCifrado;
	}

	public Instant getConectadoEm() {
		return conectadoEm;
	}

}
