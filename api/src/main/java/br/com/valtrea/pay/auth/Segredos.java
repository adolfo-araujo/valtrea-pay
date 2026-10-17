package br.com.valtrea.pay.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

/** Gera tokens e códigos aleatórios e calcula os hashes que vão para o banco. */
@Component
public class Segredos {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final SecretKey chave;

	public Segredos(SecretKey chaveJwt) {
		this.chave = chaveJwt;
	}

	/** Token de sessão: 256 bits aleatórios, impossível de adivinhar. */
	public String novoTokenSessao() {
		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String novoCodigo() {
		return "%06d".formatted(ALEATORIO.nextInt(1_000_000));
	}

	public String hashToken(String token) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Códigos de 6 dígitos têm só 1 milhão de combinações. Com HMAC (hash com a chave do servidor), quem copiar o
	 * banco não consegue descobrir o código testando todas.
	 */
	public String hashCodigo(String codigo) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(chave);
			return HexFormat.of().formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	public static boolean iguais(String a, String b) {
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}
