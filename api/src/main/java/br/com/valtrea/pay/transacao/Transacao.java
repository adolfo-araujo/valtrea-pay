package br.com.valtrea.pay.transacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.valtrea.pay.conta.Conta;
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

/** Registro imutável de uma movimentação. */
@Entity
@Table(name = "transacoes")
public class Transacao {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoTransacao tipo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "conta_origem_id")
	private Conta origem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "conta_destino_id", nullable = false)
	private Conta destino;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal valor;

	private String descricao;

	@Column(name = "chave_idempotencia")
	private String chaveIdempotencia;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	protected Transacao() {
	}

	private Transacao(TipoTransacao tipo, Conta origem, Conta destino, BigDecimal valor, String descricao,
			String chaveIdempotencia) {
		this.tipo = tipo;
		this.origem = origem;
		this.destino = destino;
		this.valor = valor;
		this.descricao = descricao;
		this.chaveIdempotencia = chaveIdempotencia;
		this.criadoEm = Instant.now();
	}

	public static Transacao transferencia(Conta origem, Conta destino, BigDecimal valor, String descricao,
			String chaveIdempotencia) {
		return new Transacao(TipoTransacao.TRANSFERENCIA, origem, destino, valor, descricao, chaveIdempotencia);
	}

	public static Transacao deposito(Conta destino, BigDecimal valor) {
		return new Transacao(TipoTransacao.DEPOSITO, null, destino, valor, "Depósito de teste", null);
	}

	public UUID getId() {
		return id;
	}

	public TipoTransacao getTipo() {
		return tipo;
	}

	public Conta getOrigem() {
		return origem;
	}

	public Conta getDestino() {
		return destino;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public String getDescricao() {
		return descricao;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
