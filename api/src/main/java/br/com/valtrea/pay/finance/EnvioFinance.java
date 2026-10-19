package br.com.valtrea.pay.finance;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um item da fila de envios para o Finance: um lançamento ou um saldo, já em JSON. */
@Entity
@Table(name = "envios_finance")
public class EnvioFinance {

	public enum Tipo {
		TRANSACAO, SALDO
	}

	static final int TENTATIVAS_MAXIMAS = 15;
	private static final Duration ESPERA_MAXIMA = Duration.ofHours(1);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "usuario_id", nullable = false)
	private UUID usuarioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Tipo tipo;

	@Column(nullable = false)
	private String conteudo;

	@Column(nullable = false)
	private int tentativas;

	@Column(name = "proxima_tentativa", nullable = false)
	private Instant proximaTentativa;

	@Column(name = "enviado_em")
	private Instant enviadoEm;

	@Column(name = "ultimo_erro")
	private String ultimoErro;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	protected EnvioFinance() {
	}

	public EnvioFinance(UUID usuarioId, Tipo tipo, String conteudo) {
		this.usuarioId = usuarioId;
		this.tipo = tipo;
		this.conteudo = conteudo;
		this.criadoEm = Instant.now();
		this.proximaTentativa = criadoEm;
	}

	public void marcarEnviado() {
		enviadoEm = Instant.now();
		ultimoErro = null;
	}

	/** Não será mais enviado (conta desconectada ou erro definitivo), mas fica registrado. */
	public void descartar(String motivo) {
		enviadoEm = Instant.now();
		ultimoErro = limitar(motivo);
	}

	/** Agenda outra tentativa: 10 s, 20 s, 40 s... até no máximo 1 hora entre elas. */
	public void falhou(String erro) {
		tentativas++;
		ultimoErro = limitar(erro);
		if (tentativas >= TENTATIVAS_MAXIMAS) {
			enviadoEm = Instant.now();
			return;
		}
		Duration espera = Duration.ofSeconds(10L << Math.min(tentativas - 1, 12));
		proximaTentativa = Instant.now().plus(espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera);
	}

	private static String limitar(String texto) {
		return texto == null ? null : texto.substring(0, Math.min(texto.length(), 200));
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public String getConteudo() {
		return conteudo;
	}

	public Instant getEnviadoEm() {
		return enviadoEm;
	}

}
