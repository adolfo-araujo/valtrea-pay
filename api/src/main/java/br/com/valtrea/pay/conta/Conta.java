package br.com.valtrea.pay.conta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Carteira de um usuário. O saldo só muda por {@link #debitar} e {@link #creditar}. */
@Entity
@Table(name = "contas")
public class Conta {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false, unique = true)
	private Usuario usuario;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldo;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Conta() {
	}

	public Conta(Usuario usuario) {
		this.usuario = usuario;
		this.saldo = BigDecimal.ZERO.setScale(2);
		this.atualizadoEm = Instant.now();
	}

	public void debitar(BigDecimal valor) {
		if (saldo.compareTo(valor) < 0) {
			throw new NegocioException(HttpStatus.UNPROCESSABLE_CONTENT, "Saldo insuficiente.");
		}
		saldo = saldo.subtract(valor);
		atualizadoEm = Instant.now();
	}

	public void creditar(BigDecimal valor) {
		saldo = saldo.add(valor);
		atualizadoEm = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public BigDecimal getSaldo() {
		return saldo;
	}

}
