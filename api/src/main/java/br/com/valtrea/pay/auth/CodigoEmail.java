package br.com.valtrea.pay.auth;

import java.time.Instant;
import java.util.UUID;

import br.com.valtrea.pay.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Código de 6 dígitos enviado por e-mail. Vale uma vez, por pouco tempo e com poucas tentativas. */
@Entity
@Table(name = "codigos_email")
public class CodigoEmail {

	public enum Finalidade {
		CONFIRMAR_EMAIL, REDEFINIR_SENHA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	private Usuario usuario;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Finalidade finalidade;

	@Column(name = "codigo_hash", nullable = false, length = 64)
	private String codigoHash;

	@Column(nullable = false)
	private int tentativas;

	@Column(name = "expira_em", nullable = false)
	private Instant expiraEm;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "usado_em")
	private Instant usadoEm;

	protected CodigoEmail() {
	}

	public CodigoEmail(Usuario usuario, Finalidade finalidade, String codigoHash, Instant expiraEm) {
		this.usuario = usuario;
		this.finalidade = finalidade;
		this.codigoHash = codigoHash;
		this.expiraEm = expiraEm;
		this.criadoEm = Instant.now();
	}

	public boolean isValido(Instant agora, int tentativasMaximas) {
		return usadoEm == null && expiraEm.isAfter(agora) && tentativas < tentativasMaximas;
	}

	public void registrarTentativa() {
		tentativas++;
	}

	public void marcarUsado() {
		usadoEm = Instant.now();
	}

	public String getCodigoHash() {
		return codigoHash;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
