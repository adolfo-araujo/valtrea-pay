package br.com.valtrea.pay.transacao;

public enum TipoTransacao {
	/** Dinheiro de um usuário para outro. */
	TRANSFERENCIA,
	/** Entrada de dinheiro na carteira. Por enquanto só existe no modo sandbox. */
	DEPOSITO
}
