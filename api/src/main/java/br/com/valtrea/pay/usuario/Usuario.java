package br.com.valtrea.pay.usuario;

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

@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false, length = 11)
	private String cpf;

	@Column(nullable = false)
	private String telefone;

	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Papel papel;

	@Column(name = "tentativas_login_falhas", nullable = false)
	private int tentativasLoginFalhas;

	@Column(name = "bloqueado_ate")
	private Instant bloqueadoAte;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String cpf, String telefone, String senhaHash) {
		this.nome = nome;
		this.email = email;
		this.cpf = cpf;
		this.telefone = telefone;
		this.senhaHash = senhaHash;
		this.papel = Papel.USUARIO;
		this.criadoEm = Instant.now();
	}

	public boolean isBloqueado(Instant agora) {
		return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
	}

	/** Conta uma senha errada e bloqueia o login por {@code bloqueio} ao atingir o limite. */
	public void registrarFalhaLogin(int tentativasMaximas, Instant bloquearAte) {
		tentativasLoginFalhas++;
		if (tentativasLoginFalhas >= tentativasMaximas) {
			bloqueadoAte = bloquearAte;
			tentativasLoginFalhas = 0;
		}
	}

	public void registrarLoginComSucesso() {
		tentativasLoginFalhas = 0;
		bloqueadoAte = null;
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getCpf() {
		return cpf;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public Papel getPapel() {
		return papel;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
