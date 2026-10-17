package br.com.valtrea.pay.auth;

import java.time.Instant;
import java.util.UUID;

import br.com.valtrea.pay.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Uma sessão do app, identificada pelo token de sessão (refresh token). Cada renovação revoga a sessão usada e cria
 * outra, então um token só vale uma vez.
 */
@Entity
@Table(name = "sessoes")
public class Sessao {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "revogado_em")
	private Instant revogadoEm;

	protected Sessao() {
	}

	public Sessao(Usuario usuario, String tokenHash, Instant expiraEm) {
		this.usuario = usuario;
		this.tokenHash = tokenHash;
		this.expiraEm = expiraEm;
		this.criadoEm = Instant.now();
	}

	public boolean isRevogada() {
		return revogadoEm != null;
	}

	public boolean isExpirada(Instant agora) {
		return !expiraEm.isAfter(agora);
	}

	public void revogar() {
		if (revogadoEm == null) {
			revogadoEm = Instant.now();
		}
	}

	public Usuario getUsuario() {
		return usuario;
	}

}
