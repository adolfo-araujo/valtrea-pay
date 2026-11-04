package br.com.valtrea.pay.protecao;

import java.io.IOException;
import java.util.OptionalLong;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.valtrea.pay.config.ValtreaProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Primeira coisa que todo pedido encontra, logo depois de o Spring corrigir o IP e o protocolo vindos do proxy do
 * Render:
 * <ul>
 * <li>cabeçalhos de segurança (as páginas de política e termos não executam nada de fora);</li>
 * <li>pedido grande demais: 413, sem nem ler o corpo;</li>
 * <li>limites por IP: geral, login e cadastro/e-mails (429 com Retry-After);</li>
 * <li>respostas lentas viram um aviso "[lento]" no log, para descobrir o problema antes de quem usa.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ProtecaoFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ProtecaoFilter.class);

	private static final Set<String> CADASTRO_E_EMAILS = Set.of("/api/v1/auth/cadastro", "/api/v1/auth/esqueci-senha",
			"/api/v1/auth/redefinir-senha", "/api/v1/auth/reenviar-confirmacao");

	private static final Set<String> AUTENTICACAO = Set.of("/api/v1/auth/login", "/api/v1/auth/renovar",
			"/api/v1/auth/confirmar-email");

	/** JSON da API: nada pode ser carregado nem exibido em moldura. */
	private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

	/** Páginas de política, termos e acessibilidade: só o próprio estilo embutido. */
	private static final String CSP_PAGINAS = "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; "
			+ "frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	private final LimiteRequisicoes limites;
	private final ValtreaProperties.Limites config;

	public ProtecaoFilter(LimiteRequisicoes limites, ValtreaProperties propriedades) {
		this.limites = limites;
		this.config = propriedades.limites();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long inicio = System.nanoTime();
		String caminho = request.getRequestURI();
		try {
			cabecalhos(caminho, response);

			if (request.getContentLengthLong() > config.tamanhoMaximoKb() * 1024L) {
				recusar(response, 413, "Payload Too Large", "O pedido é grande demais.", null);
				return;
			}

			OptionalLong espera = limitar(request, caminho);
			if (espera.isPresent()) {
				log.warn("[limite] pedidos demais em {} {}", request.getMethod(), caminho);
				recusar(response, 429, "Too Many Requests",
						"Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente de novo.", espera.getAsLong());
				return;
			}

			chain.doFilter(request, response);
		} finally {
			long ms = (System.nanoTime() - inicio) / 1_000_000;
			if (ms >= config.respostaLenta().toMillis()) {
				// Só o caminho, sem a parte depois do "?": a busca de contatos leva nomes de pessoas.
				log.warn("[lento] {} {} levou {} ms", request.getMethod(), caminho, ms);
			}
		}
	}

	private OptionalLong limitar(HttpServletRequest request, String caminho) {
		if (caminho.startsWith("/actuator/health")) {
			return OptionalLong.empty(); // o verificador que mantém o servidor acordado chama a cada poucos minutos
		}
		String ip = ipDoCliente(request);
		long agora = System.currentTimeMillis();
		boolean post = "POST".equals(request.getMethod());

		if (post && CADASTRO_E_EMAILS.contains(caminho)) {
			OptionalLong espera = limites.registrar("cadastro:" + ip, config.cadastroEEmails(), config.janelaCadastro(),
					agora);
			if (espera.isPresent()) {
				return espera;
			}
		}
		if (post && AUTENTICACAO.contains(caminho)) {
			OptionalLong espera = limites.registrar("autenticacao:" + ip, config.autenticacao(),
					config.janelaAutenticacao(), agora);
			if (espera.isPresent()) {
				return espera;
			}
		}
		return limites.registrar("geral:" + ip, config.geral(), config.janelaGeral(), agora);
	}

	/**
	 * O Render fica atrás da Cloudflare, que sempre preenche CF-Connecting-IP com o IP real (o valor enviado pelo
	 * cliente é sobrescrito). Sem ela (desenvolvimento e testes), vale o endereço da conexão.
	 */
	static String ipDoCliente(HttpServletRequest request) {
		String cloudflare = request.getHeader("CF-Connecting-IP");
		return cloudflare != null && !cloudflare.isBlank() ? cloudflare.strip() : request.getRemoteAddr();
	}

	private static void cabecalhos(String caminho, HttpServletResponse response) {
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
		response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
		if (caminho.startsWith("/swagger-ui") || caminho.startsWith("/v3/api-docs")) {
			return; // a documentação interativa carrega os próprios scripts
		}
		boolean pagina = caminho.endsWith(".html") || Set.of("/privacidade", "/termos", "/acessibilidade",
				"/excluir-conta").contains(caminho);
		response.setHeader("Content-Security-Policy", pagina ? CSP_PAGINAS : CSP_API);
	}

	private static void recusar(HttpServletResponse response, int status, String titulo, String detalhe,
			Long esperaSegundos) throws IOException {
		response.setStatus(status);
		if (esperaSegundos != null) {
			response.setHeader("Retry-After", String.valueOf(esperaSegundos));
		}
		response.setContentType("application/problem+json;charset=UTF-8");
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status
				+ ",\"detail\":\"" + detalhe + "\"}");
	}

}
