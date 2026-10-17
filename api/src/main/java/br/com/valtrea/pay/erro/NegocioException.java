package br.com.valtrea.pay.erro;

import org.springframework.http.HttpStatus;

/** Erro de regra de negócio, devolvido ao cliente com a mensagem e o status informados. */
public class NegocioException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final HttpStatus status;

	public NegocioException(HttpStatus status, String mensagem) {
		super(mensagem);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

}
