package br.com.valtrea.pay.usuario;

public final class Cpf {

	private Cpf() {
	}

	public static String somenteDigitos(String valor) {
		return valor == null ? "" : valor.replaceAll("\\D", "");
	}

	/** Confere o tamanho e os dois dígitos verificadores. */
	public static boolean isValido(String valor) {
		String cpf = somenteDigitos(valor);
		if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
			return false;
		}
		return digito(cpf, 9) == cpf.charAt(9) - '0' && digito(cpf, 10) == cpf.charAt(10) - '0';
	}

	private static int digito(String cpf, int posicao) {
		int soma = 0;
		for (int i = 0; i < posicao; i++) {
			soma += (cpf.charAt(i) - '0') * (posicao + 1 - i);
		}
		int resto = (soma * 10) % 11;
		return resto == 10 ? 0 : resto;
	}

}
