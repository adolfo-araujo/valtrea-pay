package br.com.valtrea.pay.transacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TransacaoDtos {

	private TransacaoDtos() {
	}

	public record TransferenciaRequest(
			@NotNull UUID destinoId,
			@NotNull @DecimalMin(value = "0.01", message = "o valor mínimo é R$ 0,01")
			@Digits(integer = 17, fraction = 2, message = "use no máximo 2 casas decimais") BigDecimal valor,
			@Size(max = 140) String descricao) {
	}

	public record DepositoRequest(
			@NotNull @DecimalMin(value = "0.01", message = "o valor mínimo é R$ 0,01")
			@DecimalMax(value = "10000.00", message = "o depósito de teste vai até R$ 10.000,00")
			@Digits(integer = 17, fraction = 2, message = "use no máximo 2 casas decimais") BigDecimal valor) {
	}

	public enum Direcao {
		ENTRADA, SAIDA
	}

	public record Contraparte(UUID id, String nome) {
	}

	public record TransacaoResponse(UUID id, TipoTransacao tipo, Direcao direcao, BigDecimal valor, String descricao,
			Contraparte contraparte, Instant criadoEm) {
	}

	public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens, int totalPaginas) {

		public static <T> Pagina<T> de(Page<T> page) {
			return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
					page.getTotalPages());
		}

	}

}
