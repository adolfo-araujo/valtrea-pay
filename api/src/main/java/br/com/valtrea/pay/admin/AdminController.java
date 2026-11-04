package br.com.valtrea.pay.admin;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.valtrea.pay.erro.NegocioException;
import br.com.valtrea.pay.usuario.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Painel de administração: só números agregados. Nenhum nome, e-mail, CPF, saldo ou transação individual sai daqui
 * (LGPD, princípio da necessidade). Os valores são da carteira de teste (saldo fictício).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administração")
public class AdminController {

	static final int SEMANAS = 8;

	public record Metricas(Instant geradoEm, Contas contas, Atividade atividade, Finance finance,
			List<Semana> cadastrosPorSemana) {
	}

	/**
	 * @param ativas              contas não excluídas
	 * @param confirmadas         com e-mail confirmado
	 * @param excluidasAguardando excluídas que ainda aparecem no extrato de outra pessoa (sem dados pessoais)
	 */
	public record Contas(long ativas, long confirmadas, long novas7Dias, long novas30Dias, long excluidasAguardando) {
	}

	/**
	 * @param comSessaoAtiva contas conectadas em pelo menos um aparelho
	 * @param pagantes7Dias  contas que fizeram pelo menos um pagamento nos últimos 7 dias
	 * @param volume30Dias   soma dos pagamentos entre contas nos últimos 30 dias (saldo fictício)
	 */
	public record Atividade(long comSessaoAtiva, long pagantes7Dias, long transacoes24Horas, long transacoes7Dias,
			long transacoes30Dias, BigDecimal volume30Dias) {
	}

	public record Finance(long contasConectadas, long enviosPendentes, long enviosComFalha) {
	}

	/** @param semana segunda-feira (UTC) da semana */
	public record Semana(LocalDate semana, long cadastros) {
	}

	private final JdbcTemplate jdbc;
	private final UsuarioRepository usuarios;
	private final Administradores administradores;

	public AdminController(JdbcTemplate jdbc, UsuarioRepository usuarios, Administradores administradores) {
		this.jdbc = jdbc;
		this.usuarios = usuarios;
		this.administradores = administradores;
	}

	@GetMapping("/metricas")
	@Transactional(readOnly = true)
	@Operation(summary = "Números de uso agregados (só para administradores)")
	public Metricas metricas(@AuthenticationPrincipal Jwt jwt) {
		boolean admin = usuarios.findById(UUID.fromString(jwt.getSubject())).map(administradores::ehAdmin)
				.orElse(false);
		if (!admin) {
			throw new NegocioException(HttpStatus.FORBIDDEN, "Acesso restrito à administração.");
		}
		return calcular(Instant.now());
	}

	Metricas calcular(Instant agora) {
		Timestamp agoraTs = Timestamp.from(agora);
		Timestamp h24 = Timestamp.from(agora.minus(1, ChronoUnit.DAYS));
		Timestamp d7 = Timestamp.from(agora.minus(7, ChronoUnit.DAYS));
		Timestamp d30 = Timestamp.from(agora.minus(30, ChronoUnit.DAYS));

		Contas contas = jdbc.queryForObject("""
				SELECT count(*) FILTER (WHERE excluido_em IS NULL),
				       count(*) FILTER (WHERE excluido_em IS NULL AND email_confirmado_em IS NOT NULL),
				       count(*) FILTER (WHERE excluido_em IS NULL AND criado_em >= ?),
				       count(*) FILTER (WHERE excluido_em IS NULL AND criado_em >= ?),
				       count(*) FILTER (WHERE excluido_em IS NOT NULL)
				  FROM usuarios""", (rs, i) -> new Contas(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
				rs.getLong(5)), d7, d30);

		long comSessao = jdbc.queryForObject("""
				SELECT count(DISTINCT usuario_id) FROM sessoes WHERE revogado_em IS NULL AND expira_em > ?""",
				Long.class, agoraTs);

		Atividade atividade = jdbc.queryForObject("""
				SELECT count(DISTINCT conta_origem_id) FILTER (WHERE conta_origem_id IS NOT NULL AND criado_em >= ?),
				       count(*) FILTER (WHERE criado_em >= ?),
				       count(*) FILTER (WHERE criado_em >= ?),
				       count(*) FILTER (WHERE criado_em >= ?),
				       coalesce(sum(valor) FILTER (WHERE conta_origem_id IS NOT NULL AND criado_em >= ?), 0)
				  FROM transacoes""", (rs, i) -> new Atividade(comSessao, rs.getLong(1), rs.getLong(2), rs.getLong(3),
				rs.getLong(4), rs.getBigDecimal(5)), d7, h24, d7, d30, d30);

		Finance finance = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM integracoes_finance),
				       count(*) FILTER (WHERE enviado_em IS NULL),
				       count(*) FILTER (WHERE enviado_em IS NULL AND tentativas > 0)
				  FROM envios_finance""", (rs, i) -> new Finance(rs.getLong(1), rs.getLong(2), rs.getLong(3)));

		return new Metricas(agora, contas, atividade, finance, cadastrosPorSemana(agora));
	}

	private List<Semana> cadastrosPorSemana(Instant agora) {
		LocalDate estaSemana = agora.atOffset(ZoneOffset.UTC).toLocalDate()
				.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate primeira = estaSemana.minusWeeks(SEMANAS - 1);
		Map<LocalDate, Long> porSemana = new HashMap<>();
		jdbc.query("""
				SELECT (date_trunc('week', criado_em AT TIME ZONE 'UTC'))::date AS semana, count(*)
				  FROM usuarios
				 WHERE criado_em >= ?
				 GROUP BY 1""", (RowCallbackHandler) rs -> {
			porSemana.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
		}, Timestamp.from(primeira.atStartOfDay(ZoneOffset.UTC).toInstant()));
		List<Semana> semanas = new ArrayList<>(SEMANAS);
		for (int i = 0; i < SEMANAS; i++) {
			LocalDate semana = primeira.plusWeeks(i);
			semanas.add(new Semana(semana, porSemana.getOrDefault(semana, 0L)));
		}
		return semanas;
	}

}
