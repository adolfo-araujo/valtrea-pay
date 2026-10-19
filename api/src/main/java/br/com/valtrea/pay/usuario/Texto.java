package br.com.valtrea.pay.usuario;

import java.text.Normalizer;
import java.util.Locale;

public final class Texto {

	private Texto() {
	}

	/** "  João  Araújo " → "joao araujo": sem acentos, minúsculas e espaços simples, para comparar na busca. */
	public static String paraBusca(String texto) {
		String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
	}

}
